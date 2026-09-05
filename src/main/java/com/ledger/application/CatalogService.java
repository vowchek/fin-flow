package com.ledger.application;

import com.ledger.api.dto.InstrumentResponse;
import com.ledger.api.dto.InstrumentUpsertRequest;
import com.ledger.domain.AssetMarket;
import com.ledger.domain.CatalogItem;
import com.ledger.domain.CryptoInstrument;
import com.ledger.domain.StockInstrument;
import com.ledger.infrastructure.persistence.CryptoInstrumentRepository;
import com.ledger.infrastructure.persistence.StockInstrumentRepository;
import com.ledger.infrastructure.storage.LogoStorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CatalogService {

    private static final Set<String> ALLOWED_CURRENCIES = Set.of("RUB", "USD");

    private final StockInstrumentRepository stocks;
    private final CryptoInstrumentRepository cryptos;
    private final LogoStorageService logos;

    public CatalogService(
            StockInstrumentRepository stocks,
            CryptoInstrumentRepository cryptos,
            LogoStorageService logos
    ) {
        this.stocks = stocks;
        this.cryptos = cryptos;
        this.logos = logos;
    }

    @Transactional(readOnly = true)
    public List<InstrumentResponse> listForUsers(AssetMarket market, String query) {
        String q = query == null ? "" : query.trim();
        return switch (market) {
            case MOEX -> stocks.searchEnabled(q).stream().map(this::toStockResponse).toList();
            case CRYPTO -> cryptos.searchEnabled(q).stream().map(this::toCryptoResponse).toList();
        };
    }

    @Transactional(readOnly = true)
    public List<InstrumentResponse> listForAdmin(AssetMarket market) {
        return switch (market) {
            case MOEX -> stocks.findAllByOrderBySymbolAsc().stream().map(this::toStockResponse).toList();
            case CRYPTO -> cryptos.findAllByOrderBySymbolAsc().stream().map(this::toCryptoResponse).toList();
        };
    }

    @Transactional(readOnly = true)
    public CatalogItem requireEnabled(AssetMarket market, UUID id) {
        CatalogItem item = require(market, id);
        if (!item.enabled()) {
            throw new IllegalArgumentException("Instrument is disabled: " + item.symbol());
        }
        return item;
    }

    @Transactional(readOnly = true)
    public CatalogItem require(AssetMarket market, UUID id) {
        return switch (market) {
            case MOEX -> stocks.findById(id)
                    .map(this::toCatalog)
                    .orElseThrow(() -> new ResourceNotFoundException("StockInstrument", id));
            case CRYPTO -> cryptos.findById(id)
                    .map(this::toCatalog)
                    .orElseThrow(() -> new ResourceNotFoundException("CryptoInstrument", id));
        };
    }

    @Transactional(readOnly = true)
    public CatalogItem requireBySymbol(AssetMarket market, String symbol) {
        String normalized = Symbols.normalize(symbol);
        return switch (market) {
            case MOEX -> stocks.findBySymbol(normalized)
                    .map(this::toCatalog)
                    .orElseThrow(() -> new ResourceNotFoundException("StockInstrument", normalized));
            case CRYPTO -> cryptos.findBySymbol(normalized)
                    .map(this::toCatalog)
                    .orElseThrow(() -> new ResourceNotFoundException("CryptoInstrument", normalized));
        };
    }

    @Transactional(readOnly = true)
    public CatalogItem findBySymbol(AssetMarket market, String symbol) {
        String normalized = Symbols.normalize(symbol);
        return switch (market) {
            case MOEX -> stocks.findBySymbol(normalized).map(this::toCatalog).orElse(null);
            case CRYPTO -> cryptos.findBySymbol(normalized).map(this::toCatalog).orElse(null);
        };
    }

    @Transactional
    public InstrumentResponse create(AssetMarket market, InstrumentUpsertRequest request) {
        String symbol = Symbols.normalize(request.symbol());
        String externalId = normalizeExternal(market, request.externalId());
        String name = request.name().trim();
        String currency = resolveCurrency(market, request.currency());
        boolean enabled = request.enabled() == null || request.enabled();
        try {
            return switch (market) {
                case MOEX -> {
                    StockInstrument entity = new StockInstrument(UUID.randomUUID(), symbol, externalId, name, currency);
                    entity.setEnabled(enabled);
                    applyStockCashflow(entity, request);
                    yield toStockResponse(stocks.save(entity));
                }
                case CRYPTO -> {
                    CryptoInstrument entity = new CryptoInstrument(UUID.randomUUID(), symbol, externalId, name, currency);
                    entity.setEnabled(enabled);
                    yield toCryptoResponse(cryptos.save(entity));
                }
            };
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Instrument already exists for symbol or externalId");
        }
    }

    @Transactional
    public InstrumentResponse update(AssetMarket market, UUID id, InstrumentUpsertRequest request) {
        String symbol = Symbols.normalize(request.symbol());
        String externalId = normalizeExternal(market, request.externalId());
        String name = request.name().trim();
        String currency = resolveCurrency(market, request.currency());
        try {
            return switch (market) {
                case MOEX -> {
                    StockInstrument entity = stocks.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("StockInstrument", id));
                    entity.setSymbol(symbol);
                    entity.setExternalId(externalId);
                    entity.setName(name);
                    entity.setCurrency(currency);
                    if (request.enabled() != null) {
                        entity.setEnabled(request.enabled());
                    }
                    applyStockCashflow(entity, request);
                    yield toStockResponse(entity);
                }
                case CRYPTO -> {
                    CryptoInstrument entity = cryptos.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("CryptoInstrument", id));
                    entity.setSymbol(symbol);
                    entity.setExternalId(externalId);
                    entity.setName(name);
                    entity.setCurrency(currency);
                    if (request.enabled() != null) {
                        entity.setEnabled(request.enabled());
                    }
                    yield toCryptoResponse(entity);
                }
            };
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Instrument already exists for symbol or externalId");
        }
    }

    @Transactional
    public void delete(AssetMarket market, UUID id) {
        switch (market) {
            case MOEX -> {
                StockInstrument entity = stocks.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("StockInstrument", id));
                logos.deleteIfExists(market, entity.getLogoFilename());
                stocks.delete(entity);
            }
            case CRYPTO -> {
                CryptoInstrument entity = cryptos.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("CryptoInstrument", id));
                logos.deleteIfExists(market, entity.getLogoFilename());
                cryptos.delete(entity);
            }
        }
    }

    @Transactional
    public InstrumentResponse uploadLogo(AssetMarket market, UUID id, MultipartFile file) {
        return switch (market) {
            case MOEX -> {
                StockInstrument entity = stocks.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("StockInstrument", id));
                logos.deleteIfExists(market, entity.getLogoFilename());
                entity.setLogoFilename(logos.store(market, id, file));
                yield toStockResponse(entity);
            }
            case CRYPTO -> {
                CryptoInstrument entity = cryptos.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("CryptoInstrument", id));
                logos.deleteIfExists(market, entity.getLogoFilename());
                entity.setLogoFilename(logos.store(market, id, file));
                yield toCryptoResponse(entity);
            }
        };
    }

    @Transactional(readOnly = true)
    public LogoFile loadLogo(AssetMarket market, UUID id) {
        String filename = switch (market) {
            case MOEX -> stocks.findById(id).map(StockInstrument::getLogoFilename).orElse(null);
            case CRYPTO -> cryptos.findById(id).map(CryptoInstrument::getLogoFilename).orElse(null);
        };
        if (filename == null) {
            throw new ResourceNotFoundException("Logo", id);
        }
        var path = logos.resolve(market, filename);
        if (!java.nio.file.Files.exists(path)) {
            throw new ResourceNotFoundException("Logo", id);
        }
        return new LogoFile(path, filename);
    }

    private CatalogItem toCatalog(StockInstrument i) {
        return new CatalogItem(
                AssetMarket.MOEX,
                i.getId(),
                i.getSymbol(),
                i.getExternalId(),
                i.getName(),
                i.getCurrency(),
                i.getLogoFilename() == null ? null : LogoStorageService.publicUrl(AssetMarket.MOEX, i.getId()),
                i.isEnabled()
        );
    }

    private CatalogItem toCatalog(CryptoInstrument i) {
        return new CatalogItem(
                AssetMarket.CRYPTO,
                i.getId(),
                i.getSymbol(),
                i.getExternalId(),
                i.getName(),
                i.getCurrency(),
                i.getLogoFilename() == null ? null : LogoStorageService.publicUrl(AssetMarket.CRYPTO, i.getId()),
                i.isEnabled()
        );
    }

    private InstrumentResponse toStockResponse(StockInstrument i) {
        CatalogItem item = toCatalog(i);
        return new InstrumentResponse(
                item.id(),
                item.market().name(),
                item.symbol(),
                item.externalId(),
                item.name(),
                item.logoUrl(),
                item.currency(),
                item.enabled(),
                i.getAssetKind(),
                i.getAnnualCashflowPerUnit(),
                i.getCashflowGrowthPct(),
                i.getCashflowUntilYear(),
                i.isPaysDividends()
        );
    }

    private InstrumentResponse toCryptoResponse(CryptoInstrument i) {
        CatalogItem item = toCatalog(i);
        return new InstrumentResponse(
                item.id(),
                item.market().name(),
                item.symbol(),
                item.externalId(),
                item.name(),
                item.logoUrl(),
                item.currency(),
                item.enabled(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private static void applyStockCashflow(StockInstrument entity, InstrumentUpsertRequest request) {
        if (request.assetKind() != null) {
            entity.setAssetKind(request.assetKind());
        }
        if (request.paysDividends() != null) {
            entity.setPaysDividends(request.paysDividends());
        }
    }

    private static String normalizeExternal(AssetMarket market, String externalId) {
        String value = externalId.trim();
        return market == AssetMarket.MOEX ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT);
    }

    private static String resolveCurrency(AssetMarket market, String raw) {
        if (raw == null || raw.isBlank()) {
            return market == AssetMarket.CRYPTO ? "USD" : "RUB";
        }
        String currency = raw.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException("Currency must be RUB or USD");
        }
        return currency;
    }

    public record LogoFile(java.nio.file.Path path, String filename) {
    }
}
