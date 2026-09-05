package com.ledger.application;

import com.ledger.api.dto.InstrumentImportRequest;
import com.ledger.api.dto.InstrumentImportResponse;
import com.ledger.api.dto.InstrumentPaymentResponse;
import com.ledger.api.dto.InstrumentPaymentsRefreshResponse;
import com.ledger.api.dto.InstrumentResponse;
import com.ledger.api.dto.RemoteInstrumentResponse;
import com.ledger.domain.AssetKind;
import com.ledger.domain.AssetMarket;
import com.ledger.domain.InstrumentPaymentKind;
import com.ledger.domain.StockInstrument;
import com.ledger.domain.StockInstrumentPayment;
import com.ledger.infrastructure.marketdata.MarketDataProvider;
import com.ledger.infrastructure.marketdata.TinvestClient;
import com.ledger.infrastructure.persistence.StockInstrumentPaymentRepository;
import com.ledger.infrastructure.persistence.StockInstrumentRepository;
import com.ledger.infrastructure.storage.LogoStorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class InstrumentImportService {

    private final StockInstrumentRepository stocks;
    private final StockInstrumentPaymentRepository payments;
    private final Optional<TinvestClient> tinvest;
    private final TransactionTemplate transactionTemplate;

    public InstrumentImportService(
            StockInstrumentRepository stocks,
            StockInstrumentPaymentRepository payments,
            Optional<TinvestClient> tinvest,
            PlatformTransactionManager transactionManager
    ) {
        this.stocks = stocks;
        this.payments = payments;
        this.tinvest = tinvest;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<RemoteInstrumentResponse> searchMarket(String query) {
        TinvestClient client = requireTinvest();
        return client.search(query).stream()
                .map(h -> new RemoteInstrumentResponse(
                        h.ticker(),
                        h.ticker(),
                        h.name() + (h.classCode() != null ? " · " + h.classCode() : ""),
                        "RUB",
                        null
                ))
                .toList();
    }

    /** @deprecated use {@link #searchMarket(String)} */
    @Transactional(readOnly = true)
    public List<RemoteInstrumentResponse> searchMoex(String query) {
        return searchMarket(query);
    }

    @Transactional
    public InstrumentImportResponse importFromMarket(InstrumentImportRequest request) {
        TinvestClient client = requireTinvest();
        String key = resolveExternalId(request);
        boolean enabled = request.enabled() == null || request.enabled();

        TinvestClient.RemoteHit hit = client.resolve(key)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found in T-Invest: " + key));
        TinvestClient.InstrumentDetails details = client.fetchDetails(hit).orElseGet(() ->
                new TinvestClient.InstrumentDetails(
                        hit.ticker(),
                        hit.ticker(),
                        hit.name(),
                        "RUB",
                        hit.assetKind(),
                        hit.uid(),
                        hit.figi(),
                        hit.isin(),
                        hit.classCode(),
                        null
                ));

        StockInstrument entity = stocks.findByExternalId(details.externalId())
                .or(() -> stocks.findBySymbol(Symbols.normalize(details.symbol())))
                .orElseGet(() -> new StockInstrument(
                        UUID.randomUUID(),
                        Symbols.normalize(details.symbol()),
                        details.externalId().toUpperCase(Locale.ROOT),
                        details.name(),
                        resolveCurrency(details.currency())
                ));

        entity.setSymbol(Symbols.normalize(details.symbol()));
        entity.setExternalId(details.externalId().toUpperCase(Locale.ROOT));
        entity.setName(details.name());
        entity.setCurrency(resolveCurrency(details.currency()));
        entity.setEnabled(enabled);
        AssetKind kind = parseAssetKind(details.assetKind());
        if (kind != null) {
            entity.setAssetKind(kind);
        }

        try {
            entity = stocks.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Instrument already exists for symbol or externalId");
        }

        // Always replace payment history on import/refresh.
        List<MarketDataProvider.CorporatePayment> remote = client.fetchPayments(hit);
        List<StockInstrumentPayment> savedPayments = replacePayments(entity, remote);

        if ((kind == null || kind == AssetKind.EQUITY)
                && savedPayments.stream().anyMatch(p -> p.getKind() == InstrumentPaymentKind.COUPON)
                && savedPayments.stream().noneMatch(p -> p.getKind() == InstrumentPaymentKind.DIVIDEND)) {
            entity.setAssetKind(AssetKind.BOND);
        }

        return new InstrumentImportResponse(toResponse(entity), toPaymentResponses(savedPayments));
    }

    /** @deprecated use {@link #importFromMarket(InstrumentImportRequest)} */
    @Transactional
    public InstrumentImportResponse importFromMoex(InstrumentImportRequest request) {
        return importFromMarket(request);
    }

    @Transactional
    public InstrumentImportResponse refreshFromMarket(UUID instrumentId, Boolean overwriteCashflow) {
        StockInstrument entity = stocks.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("StockInstrument", instrumentId));
        InstrumentImportRequest request = new InstrumentImportRequest(
                entity.getExternalId(),
                entity.getSymbol(),
                entity.isEnabled(),
                overwriteCashflow == null || overwriteCashflow
        );
        return importFromMarket(request);
    }

    /** Refresh only payment history for every stock instrument (no metadata overwrite beyond payments). */
    public InstrumentPaymentsRefreshResponse refreshAllPayments() {
        TinvestClient client = requireTinvest();
        List<StockInstrument> all = stocks.findAllByOrderBySymbolAsc();
        List<InstrumentPaymentsRefreshResponse.Item> results = new ArrayList<>();
        int ok = 0;
        int failed = 0;
        for (StockInstrument entity : all) {
            UUID id = entity.getId();
            String symbol = entity.getSymbol();
            try {
                Integer count = transactionTemplate.execute(status -> {
                    StockInstrument managed = stocks.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Instrument missing"));
                    TinvestClient.RemoteHit hit = client.resolve(managed.getExternalId())
                            .or(() -> client.resolve(managed.getSymbol()))
                            .orElseThrow(() -> new IllegalArgumentException("Not found in T-Invest"));
                    List<MarketDataProvider.CorporatePayment> remote = client.fetchPayments(hit);
                    return replacePayments(managed, remote).size();
                });
                ok++;
                results.add(new InstrumentPaymentsRefreshResponse.Item(
                        id, symbol, true, null, count == null ? 0 : count));
            } catch (Exception ex) {
                failed++;
                results.add(new InstrumentPaymentsRefreshResponse.Item(
                        id,
                        symbol,
                        false,
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                        0
                ));
            }
        }
        return new InstrumentPaymentsRefreshResponse(ok, failed, results);
    }

    /** @deprecated use {@link #refreshFromMarket(UUID, Boolean)} */
    @Transactional
    public InstrumentImportResponse refreshFromMoex(UUID instrumentId, Boolean overwriteCashflow) {
        return refreshFromMarket(instrumentId, overwriteCashflow);
    }

    @Transactional(readOnly = true)
    public List<InstrumentPaymentResponse> listPayments(UUID instrumentId) {
        if (!stocks.existsById(instrumentId)) {
            throw new ResourceNotFoundException("StockInstrument", instrumentId);
        }
        return toPaymentResponses(payments.findByInstrumentIdOrderByOccurredOnDesc(instrumentId));
    }

    private TinvestClient requireTinvest() {
        TinvestClient client = tinvest.orElse(null);
        if (client == null || !client.isConfigured()) {
            throw new IllegalStateException("TINVEST_TOKEN is not configured");
        }
        return client;
    }

    private List<StockInstrumentPayment> replacePayments(
            StockInstrument entity,
            List<MarketDataProvider.CorporatePayment> remote
    ) {
        payments.deleteByInstrumentId(entity.getId());
        if (remote == null || remote.isEmpty()) {
            return List.of();
        }
        Map<String, StockInstrumentPayment> unique = new LinkedHashMap<>();
        for (MarketDataProvider.CorporatePayment p : remote) {
            if (p.date() == null || p.valuePerUnit() == null || p.valuePerUnit().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            InstrumentPaymentKind kind = parsePaymentKind(p.kind());
            if (kind == null) {
                continue;
            }
            String currency = resolveCurrency(p.currency());
            String key = p.date() + "|" + kind + "|" + p.valuePerUnit().stripTrailingZeros().toPlainString();
            unique.putIfAbsent(key, new StockInstrumentPayment(
                    UUID.randomUUID(),
                    entity,
                    p.date(),
                    p.valuePerUnit(),
                    currency,
                    kind
            ));
        }
        List<StockInstrumentPayment> rows = new ArrayList<>(unique.values());
        rows.sort(Comparator
                .comparing(StockInstrumentPayment::getOccurredOn).reversed()
                .thenComparing(p -> p.getKind().name()));
        return payments.saveAll(rows);
    }

    private static String resolveExternalId(InstrumentImportRequest request) {
        if (request.externalId() != null && !request.externalId().isBlank()) {
            return request.externalId().trim().toUpperCase(Locale.ROOT);
        }
        if (request.symbol() != null && !request.symbol().isBlank()) {
            return request.symbol().trim().toUpperCase(Locale.ROOT);
        }
        throw new IllegalArgumentException("externalId or symbol is required");
    }

    private static AssetKind parseAssetKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AssetKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static InstrumentPaymentKind parsePaymentKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return InstrumentPaymentKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String resolveCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return "RUB";
        }
        String currency = raw.trim().toUpperCase(Locale.ROOT);
        if ("SUR".equals(currency)) {
            return "RUB";
        }
        if (!"RUB".equals(currency) && !"USD".equals(currency)) {
            return "RUB";
        }
        return currency;
    }

    private InstrumentResponse toResponse(StockInstrument i) {
        return new InstrumentResponse(
                i.getId(),
                AssetMarket.MOEX.name(),
                i.getSymbol(),
                i.getExternalId(),
                i.getName(),
                i.getLogoFilename() == null ? null : LogoStorageService.publicUrl(AssetMarket.MOEX, i.getId()),
                i.getCurrency(),
                i.isEnabled(),
                i.getAssetKind(),
                i.getAnnualCashflowPerUnit(),
                i.getCashflowGrowthPct(),
                i.getCashflowUntilYear(),
                i.isPaysDividends()
        );
    }

    private static List<InstrumentPaymentResponse> toPaymentResponses(List<StockInstrumentPayment> rows) {
        return rows.stream()
                .map(p -> new InstrumentPaymentResponse(
                        p.getId(),
                        p.getOccurredOn(),
                        p.getAmountPerUnit(),
                        p.getCurrency(),
                        p.getKind()
                ))
                .toList();
    }
}
