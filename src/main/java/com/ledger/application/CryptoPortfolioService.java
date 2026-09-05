package com.ledger.application;

import com.ledger.api.dto.CashMovementRequest;
import com.ledger.api.dto.HoldingCreateRequest;
import com.ledger.api.dto.HoldingDetailResponse;
import com.ledger.api.dto.HoldingResponse;
import com.ledger.api.dto.LazyCryptoSeedRequest;
import com.ledger.api.dto.PortfolioDetailResponse;
import com.ledger.api.dto.PortfolioRequest;
import com.ledger.api.dto.PortfolioSummaryResponse;
import com.ledger.api.dto.TradeRequest;
import com.ledger.api.dto.TradeResponse;
import com.ledger.api.dto.ValuePointResponse;
import com.ledger.domain.AppUser;
import com.ledger.domain.AssetMarket;
import com.ledger.domain.CryptoHolding;
import com.ledger.domain.CryptoPortfolio;
import com.ledger.domain.CryptoTransaction;
import com.ledger.domain.CatalogItem;
import com.ledger.domain.TradeSide;
import com.ledger.domain.TxKind;
import com.ledger.infrastructure.marketdata.MarketDataProperties;
import com.ledger.infrastructure.persistence.CryptoHoldingRepository;
import com.ledger.infrastructure.persistence.CryptoPortfolioRepository;
import com.ledger.infrastructure.persistence.CryptoTransactionRepository;
import com.ledger.infrastructure.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CryptoPortfolioService {

    private static final AssetMarket MARKET = AssetMarket.CRYPTO;

    private final CryptoPortfolioRepository portfolios;
    private final CryptoHoldingRepository holdings;
    private final CryptoTransactionRepository transactions;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final CatalogService catalog;
    private final QuoteService quotes;
    private final PortfolioValueHistoryService valueHistoryService;
    private final MarketDataProperties marketDataProperties;

    public CryptoPortfolioService(
            CryptoPortfolioRepository portfolios,
            CryptoHoldingRepository holdings,
            CryptoTransactionRepository transactions,
            UserRepository users,
            CurrentUserService currentUser,
            CatalogService catalog,
            QuoteService quotes,
            PortfolioValueHistoryService valueHistoryService,
            MarketDataProperties marketDataProperties
    ) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.transactions = transactions;
        this.users = users;
        this.currentUser = currentUser;
        this.catalog = catalog;
        this.quotes = quotes;
        this.valueHistoryService = valueHistoryService;
        this.marketDataProperties = marketDataProperties;
    }

    @Transactional
    public List<PortfolioSummaryResponse> list() {
        return portfolios.findAllByOwnerIdOrderByNameAsc(currentUser.requireUserId()).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public PortfolioDetailResponse get(UUID id) {
        return toDetail(requireOwned(id));
    }

    @Transactional
    public List<ValuePointResponse> valueHistory(UUID id) {
        CryptoPortfolio portfolio = requireOwned(id);
        ensureCashHolding(portfolio);
        List<PortfolioValueHistoryService.PositionTrades> positions = portfolio.getHoldings().stream()
                .map(holding -> new PortfolioValueHistoryService.PositionTrades(
                        holding.getSymbol(),
                        transactions.findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(holding.getId()).stream()
                                .sorted(Comparator
                                        .comparing(CryptoTransaction::getOccurredOn)
                                        .thenComparing(CryptoTransaction::getCreatedAt))
                                .map(tx -> new PortfolioValueHistoryService.TradeLeg(
                                        tx.getSide(),
                                        tx.getKind(),
                                        tx.getQuantity(),
                                        tx.getUnitPrice(),
                                        tx.getOccurredOn()
                                ))
                                .toList()
                ))
                .filter(p -> !p.trades().isEmpty())
                .toList();
        return valueHistoryService.build(MARKET, positions);
    }

    @Transactional
    public PortfolioDetailResponse create(PortfolioRequest request) {
        AppUser owner = requireOwner();
        CryptoPortfolio portfolio = new CryptoPortfolio(
                UUID.randomUUID(),
                owner,
                request.name().trim(),
                Strings.trimToNull(request.description()),
                PortfolioEntryModes.parse(request.entryMode())
        );
        portfolio = portfolios.save(portfolio);
        ensureCashHolding(portfolio);
        return toDetail(portfolio);
    }

    @Transactional
    public PortfolioDetailResponse update(UUID id, PortfolioRequest request) {
        CryptoPortfolio portfolio = requireOwned(id);
        portfolio.setName(request.name().trim());
        portfolio.setDescription(Strings.trimToNull(request.description()));
        if (request.investedAmount() != null) {
            portfolio.setInvestedAmount(request.investedAmount());
        }
        return toDetail(portfolio);
    }

    @Transactional
    public void delete(UUID id) {
        portfolios.delete(requireOwned(id));
    }

    @Transactional
    public PortfolioDetailResponse seedLazy(UUID portfolioId, LazyCryptoSeedRequest request) {
        CryptoPortfolio portfolio = requireOwned(portfolioId);
        ensureCashHolding(portfolio);
        boolean hasNonCash = portfolio.getHoldings().stream().anyMatch(h -> !h.isCash());
        if (hasNonCash) {
            portfolio.getHoldings().removeIf(h -> !h.isCash());
            holdings.flush();
        }

        record Prepared(CatalogItem instrument, BigDecimal quantity, BigDecimal livePrice, BigDecimal marketValue) {
        }

        List<Prepared> prepared = new ArrayList<>();
        List<CatalogItem> instruments = new ArrayList<>();
        List<LazyCryptoSeedRequest.Item> seedItems = request.holdings();
        Map<UUID, LazyCryptoSeedRequest.Item> itemByInstrument = new LinkedHashMap<>();
        for (LazyCryptoSeedRequest.Item item : seedItems) {
            CatalogItem instrument = resolveInstrument(new HoldingCreateRequest(
                    item.instrumentId(),
                    item.symbol(),
                    null,
                    item.quantity(),
                    request.startedOn(),
                    null,
                    null,
                    false
            ));
            instruments.add(instrument);
            itemByInstrument.put(instrument.id(), item);
        }

        Map<UUID, QuoteService.LiveQuote> liveQuotes = quotes.getLiveMany(instruments);
        for (CatalogItem instrument : instruments) {
            LazyCryptoSeedRequest.Item item = itemByInstrument.get(instrument.id());
            QuoteService.LiveQuote live = liveQuotes.get(instrument.id());
            if (live == null) {
                throw new IllegalArgumentException(
                        "Нет котировки для " + instrument.symbol()
                                + ". CoinGecko временно ограничил запросы — подождите минуту и повторите."
                );
            }
            BigDecimal livePrice = live.price();
            BigDecimal marketValue = livePrice.multiply(item.quantity());
            prepared.add(new Prepared(instrument, item.quantity(), livePrice, marketValue));
        }

        BigDecimal totalMarketValue = prepared.stream()
                .map(Prepared::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total market value must be positive to allocate invested amount");
        }

        MathContext mc = new MathContext(16, RoundingMode.HALF_UP);
        for (Prepared item : prepared) {
            BigDecimal share = item.marketValue().divide(totalMarketValue, mc);
            BigDecimal cost = request.investedAmount().multiply(share, mc);
            BigDecimal unitPrice = cost.divide(item.quantity(), 8, RoundingMode.HALF_UP);
            openHolding(portfolioId, new HoldingCreateRequest(
                    item.instrument().id(),
                    item.instrument().symbol(),
                    item.instrument().name(),
                    item.quantity(),
                    request.startedOn(),
                    unitPrice,
                    "lazy-seed",
                    false
            ));
        }
        portfolio.setInvestedAmount(request.investedAmount());
        return toDetail(requireOwned(portfolioId));
    }

    @Transactional
    public HoldingResponse openHolding(UUID portfolioId, HoldingCreateRequest request) {
        CryptoPortfolio portfolio = requireOwned(portfolioId);
        ensureCashHolding(portfolio);
        CatalogItem instrument = resolveInstrument(request);
        rejectCashSymbol(instrument.symbol());
        if (holdings.existsByPortfolioIdAndSymbol(portfolio.getId(), instrument.symbol())) {
            throw new ConflictException("Holding already exists for symbol: " + instrument.symbol());
        }
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        CryptoHolding holding = new CryptoHolding(
                UUID.randomUUID(),
                portfolio,
                instrument.symbol(),
                instrument.name(),
                BigDecimal.ZERO
        );
        try {
            holding = holdings.save(holding);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Holding already exists for symbol: " + instrument.symbol());
        }
        BigDecimal price = quotes.resolveTradePrice(instrument, occurredOn, request.unitPrice());
        applyTrade(holding, instrument, TradeSide.BUY, request.quantity(), occurredOn, price, request.note());
        addToInvestedIfNeeded(portfolio, request.quantity().multiply(price), request.addToInvested());
        return toHolding(holding);
    }

    @Transactional
    public HoldingResponse buy(UUID portfolioId, UUID holdingId, TradeRequest request) {
        CryptoHolding holding = requireOwnedHolding(portfolioId, holdingId);
        rejectCashHolding(holding);
        CatalogItem instrument = catalog.requireBySymbol(MARKET, holding.getSymbol());
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        BigDecimal price = quotes.resolveTradePrice(instrument, occurredOn, request.unitPrice());
        applyTrade(holding, instrument, TradeSide.BUY, request.quantity(), occurredOn, price, request.note());
        addToInvestedIfNeeded(holding.getPortfolio(), request.quantity().multiply(price), request.addToInvested());
        return toHolding(holding);
    }

    @Transactional
    public HoldingResponse sell(UUID portfolioId, UUID holdingId, TradeRequest request) {
        CryptoHolding holding = requireOwnedHolding(portfolioId, holdingId);
        rejectCashHolding(holding);
        CatalogItem instrument = catalog.requireBySymbol(MARKET, holding.getSymbol());
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        if (holding.getQuantity().compareTo(request.quantity()) < 0) {
            throw new IllegalArgumentException("Sell quantity exceeds current holding quantity");
        }
        applyTrade(holding, instrument, TradeSide.SELL, request.quantity(), occurredOn, request.unitPrice(), request.note());
        return toHolding(holding);
    }

    /** Remove position and its trades without recording a sell (cash stays as-is). */
    @Transactional
    public void deleteHolding(UUID portfolioId, UUID holdingId) {
        CryptoHolding holding = requireOwnedHolding(portfolioId, holdingId);
        rejectCashHolding(holding);
        holdings.delete(holding);
    }

    @Transactional
    public HoldingResponse depositCash(UUID portfolioId, CashMovementRequest request) {
        return moveCash(portfolioId, request, TxKind.DEPOSIT);
    }

    @Transactional
    public HoldingResponse withdrawCash(UUID portfolioId, CashMovementRequest request) {
        return moveCash(portfolioId, request, TxKind.WITHDRAW);
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> listTransactions(UUID portfolioId, UUID holdingId) {
        requireOwnedHolding(portfolioId, holdingId);
        return transactions.findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(holdingId).stream()
                .map(this::toTrade)
                .toList();
    }

    private HoldingResponse moveCash(UUID portfolioId, CashMovementRequest request, TxKind kind) {
        CryptoPortfolio portfolio = requireOwned(portfolioId);
        CryptoHolding cash = ensureCashHolding(portfolio);
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        TradeSide side = kind == TxKind.WITHDRAW ? TradeSide.SELL : TradeSide.BUY;
        BigDecimal unitPrice = BigDecimal.ONE;

        if (kind == TxKind.WITHDRAW && cash.getQuantity().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Withdraw amount exceeds cash balance");
        }

        applyCashTx(cash, side, kind, request.amount(), occurredOn, unitPrice, request.note());
        return toHolding(cash);
    }

    private void applyCashTx(
            CryptoHolding cash,
            TradeSide side,
            TxKind kind,
            BigDecimal quantity,
            LocalDate occurredOn,
            BigDecimal unitPrice,
            String note
    ) {
        CryptoTransaction tx = new CryptoTransaction(
                UUID.randomUUID(),
                cash,
                side,
                kind,
                null,
                quantity,
                occurredOn,
                unitPrice,
                Strings.trimToNull(note)
        );
        transactions.save(tx);
        if (side == TradeSide.BUY) {
            cash.setQuantity(cash.getQuantity().add(quantity));
        } else {
            cash.setQuantity(cash.getQuantity().subtract(quantity));
        }
    }

    private CryptoHolding ensureCashHolding(CryptoPortfolio portfolio) {
        return holdings.findByPortfolioIdAndCashTrue(portfolio.getId())
                .orElseGet(() -> {
                    CryptoHolding cash = new CryptoHolding(
                            UUID.randomUUID(),
                            portfolio,
                            CashSupport.symbol(MARKET),
                            CashSupport.name(MARKET),
                            BigDecimal.ZERO,
                            true
                    );
                    cash = holdings.save(cash);
                    portfolio.getHoldings().add(cash);
                    return cash;
                });
    }

    private void rejectCashHolding(CryptoHolding holding) {
        if (holding.isCash() || isCashSymbol(holding.getSymbol())) {
            throw new IllegalArgumentException("Cash holding cannot be traded as a position");
        }
    }

    private void rejectCashSymbol(String symbol) {
        if (isCashSymbol(symbol)) {
            throw new IllegalArgumentException("Cash holding cannot be traded as a position");
        }
    }

    private boolean isCashSymbol(String symbol) {
        return CashSupport.symbol(MARKET).equalsIgnoreCase(symbol);
    }

    private CatalogItem resolveInstrument(HoldingCreateRequest request) {
        if (request.instrumentId() != null) {
            return catalog.requireEnabled(MARKET, request.instrumentId());
        }
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new IllegalArgumentException("instrumentId or symbol is required");
        }
        CatalogItem item = catalog.requireBySymbol(MARKET, request.symbol());
        if (!item.enabled()) {
            throw new IllegalArgumentException("Instrument is disabled: " + item.symbol());
        }
        return item;
    }

    private void applyTrade(
            CryptoHolding holding,
            CatalogItem instrument,
            TradeSide side,
            BigDecimal quantity,
            LocalDate occurredOn,
            BigDecimal unitPrice,
            String note
    ) {
        BigDecimal price = unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0
                ? unitPrice
                : quotes.resolveTradePrice(instrument, occurredOn, unitPrice);
        CryptoTransaction tx = new CryptoTransaction(
                UUID.randomUUID(),
                holding,
                side,
                quantity,
                occurredOn,
                price,
                Strings.trimToNull(note)
        );
        transactions.save(tx);
        if (side == TradeSide.BUY) {
            holding.setQuantity(holding.getQuantity().add(quantity));
        } else {
            holding.setQuantity(holding.getQuantity().subtract(quantity));
        }
    }

    private void addToInvestedIfNeeded(CryptoPortfolio portfolio, BigDecimal spend, Boolean addToInvested) {
        if (addToInvested != null && !addToInvested) {
            return;
        }
        if (spend == null || spend.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal current = portfolio.getInvestedAmount() == null ? BigDecimal.ZERO : portfolio.getInvestedAmount();
        portfolio.setInvestedAmount(current.add(spend));
    }

    @Transactional
    public HoldingDetailResponse holdingDetail(UUID portfolioId, UUID holdingId) {
        CryptoHolding holding = requireOwnedHolding(portfolioId, holdingId);
        HoldingResponse valued = toHolding(holding, Map.of(), false);

        List<ValuePointResponse> priceHistory = List.of();
        if (!holding.isCash()) {
            CatalogItem item = catalog.findBySymbol(MARKET, holding.getSymbol());
            if (item != null) {
                LocalDate to = LocalDate.now();
                LocalDate from = to.minusYears(5);
                quotes.ensureHistory(item, from, to);
                priceHistory = quotes.priceSeries(item, from, to).entrySet().stream()
                        .map(e -> new ValuePointResponse(e.getKey(), e.getValue(), null))
                        .toList();
            }
        }

        BigDecimal unitDayAbs = null;
        if (valued.unitPrice() != null
                && valued.dayChangeAbs() != null
                && valued.quantity() != null
                && valued.quantity().compareTo(BigDecimal.ZERO) != 0) {
            unitDayAbs = valued.dayChangeAbs()
                    .divide(valued.quantity(), 8, RoundingMode.HALF_UP)
                    .setScale(4, RoundingMode.HALF_UP);
        }

        return new HoldingDetailResponse(
                valued.id(),
                valued.symbol(),
                valued.name(),
                valued.logoUrl(),
                valued.cash(),
                valued.quantity(),
                valued.unitPrice(),
                unitDayAbs,
                valued.dayChangePct(),
                valued.marketValue(),
                valued.currency(),
                priceHistory,
                null,
                null,
                null,
                null
        );
    }

    private CryptoHolding requireOwnedHolding(UUID portfolioId, UUID holdingId) {
        requireOwned(portfolioId);
        return holdings.findByIdAndPortfolioId(holdingId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("CryptoHolding", holdingId));
    }

    private CryptoPortfolio requireOwned(UUID id) {
        return portfolios.findByIdAndOwnerId(id, currentUser.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException("CryptoPortfolio", id));
    }

    private AppUser requireOwner() {
        UUID ownerId = currentUser.requireUserId();
        return users.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("User", ownerId));
    }

    private PortfolioSummaryResponse toSummary(CryptoPortfolio portfolio) {
        ensureCashHolding(portfolio);
        List<HoldingResponse> holdingResponses = enrichHoldings(portfolio);
        long active = holdingResponses.stream()
                .filter(h -> h.quantity().compareTo(BigDecimal.ZERO) > 0)
                .count();
        return PortfolioMetrics.summary(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getEntryMode(),
                (int) active,
                holdingResponses,
                marketDataProperties.quoteCurrency(MARKET),
                portfolio.getInvestedAmount(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }

    private PortfolioDetailResponse toDetail(CryptoPortfolio portfolio) {
        ensureCashHolding(portfolio);
        List<HoldingResponse> holdings = enrichHoldings(portfolio);
        return PortfolioMetrics.detail(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getEntryMode(),
                holdings,
                List.of(),
                marketDataProperties.quoteCurrency(MARKET),
                null,
                portfolio.getInvestedAmount(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }

    private List<HoldingResponse> enrichHoldings(CryptoPortfolio portfolio) {
        List<CryptoHolding> ordered = portfolio.getHoldings().stream()
                .sorted(Comparator
                        .comparing(CryptoHolding::isCash)
                        .thenComparing((CryptoHolding h) -> h.getQuantity().compareTo(BigDecimal.ZERO) > 0).reversed()
                        .thenComparing(CryptoHolding::getSymbol))
                .toList();
        List<CatalogItem> catalogItems = ordered.stream()
                .filter(h -> !h.isCash())
                .map(h -> catalog.findBySymbol(MARKET, h.getSymbol()))
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, QuoteService.LiveQuote> liveQuotes = quotes.getLiveMany(catalogItems);
        return ordered.stream()
                .map(holding -> toHolding(holding, liveQuotes, true))
                .toList();
    }

    private HoldingResponse toHolding(CryptoHolding holding) {
        return toHolding(holding, Map.of(), false);
    }

    private HoldingResponse toHolding(
            CryptoHolding holding,
            Map<UUID, QuoteService.LiveQuote> liveQuotes,
            boolean usePrefetchedQuotes
    ) {
        LocalDate openedOn = transactions
                .findFirstByHoldingIdAndSideOrderByOccurredOnAscCreatedAtAsc(holding.getId(), TradeSide.BUY)
                .map(CryptoTransaction::getOccurredOn)
                .orElse(null);
        HoldingResponse base = new HoldingResponse(
                holding.getId(),
                holding.getSymbol(),
                holding.getName(),
                holding.getQuantity(),
                openedOn,
                holding.isCash(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                holding.getCreatedAt(),
                holding.getUpdatedAt(),
                null,
                null
        );
        java.util.Optional<QuoteService.LiveQuote> liveOverride = null;
        if (usePrefetchedQuotes && !holding.isCash()) {
            CatalogItem item = catalog.findBySymbol(MARKET, holding.getSymbol());
            liveOverride = java.util.Optional.ofNullable(item == null ? null : liveQuotes.get(item.id()));
        }
        return HoldingValuation.enrich(base, MARKET, catalog, quotes, tradesFor(holding.getId()), null, liveOverride);
    }

    private List<HoldingValuation.TradeInfo> tradesFor(UUID holdingId) {
        return transactions
                .findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(holdingId)
                .stream()
                .sorted(Comparator
                        .comparing(CryptoTransaction::getOccurredOn)
                        .thenComparing(CryptoTransaction::getCreatedAt))
                .map(tx -> new HoldingValuation.TradeInfo(
                        tx.getSide(),
                        tx.getKind(),
                        tx.getQuantity(),
                        tx.getUnitPrice(),
                        tx.getOccurredOn()
                ))
                .toList();
    }

    private TradeResponse toTrade(CryptoTransaction tx) {
        return new TradeResponse(
                tx.getId(),
                tx.getSide(),
                tx.getKind() == null ? TxKind.TRADE : tx.getKind(),
                tx.getQuantity(),
                tx.getOccurredOn(),
                tx.getUnitPrice(),
                tx.getNote(),
                true,
                tx.getCreatedAt()
        );
    }
}
