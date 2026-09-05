package com.ledger.application;

import com.ledger.api.dto.CashMovementRequest;
import com.ledger.api.dto.HoldingCashflowRequest;
import com.ledger.api.dto.HoldingCashflowResponse;
import com.ledger.api.dto.HoldingCreateRequest;
import com.ledger.api.dto.HoldingResponse;
import com.ledger.api.dto.LazyStockSeedRequest;
import com.ledger.api.dto.PassiveIncomeResponse;
import com.ledger.api.dto.PaymentCalendarResponse;
import com.ledger.api.dto.PortfolioDetailResponse;
import com.ledger.api.dto.PortfolioRequest;
import com.ledger.api.dto.PortfolioSettingsRequest;
import com.ledger.api.dto.PortfolioSettingsResponse;
import com.ledger.api.dto.PortfolioSummaryResponse;
import com.ledger.api.dto.TradeRequest;
import com.ledger.api.dto.TradeResponse;
import com.ledger.api.dto.ValuePointResponse;
import com.ledger.domain.AppUser;
import com.ledger.domain.AssetKind;
import com.ledger.domain.AssetMarket;
import com.ledger.domain.CatalogItem;
import com.ledger.domain.StockHolding;
import com.ledger.domain.StockInstrument;
import com.ledger.domain.StockPortfolio;
import com.ledger.domain.StockTransaction;
import com.ledger.domain.TradeSide;
import com.ledger.domain.TxKind;
import com.ledger.infrastructure.marketdata.MarketDataProperties;
import com.ledger.infrastructure.persistence.StockHoldingRepository;
import com.ledger.infrastructure.persistence.StockInstrumentRepository;
import com.ledger.infrastructure.persistence.StockPortfolioRepository;
import com.ledger.infrastructure.persistence.StockTransactionRepository;
import com.ledger.infrastructure.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class StockPortfolioService {

    private static final AssetMarket MARKET = AssetMarket.MOEX;
    private static final List<TxKind> INCOME_KINDS = List.of(TxKind.DIVIDEND, TxKind.COUPON);

    private final StockPortfolioRepository portfolios;
    private final StockHoldingRepository holdings;
    private final StockTransactionRepository transactions;
    private final StockInstrumentRepository instruments;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final CatalogService catalog;
    private final QuoteService quotes;
    private final PortfolioValueHistoryService valueHistoryService;
    private final MarketDataProperties marketDataProperties;

    public StockPortfolioService(
            StockPortfolioRepository portfolios,
            StockHoldingRepository holdings,
            StockTransactionRepository transactions,
            StockInstrumentRepository instruments,
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
        this.instruments = instruments;
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
        StockPortfolio portfolio = requireOwned(id);
        ensureCashHolding(portfolio);
        List<PortfolioValueHistoryService.PositionTrades> positions = portfolio.getHoldings().stream()
                .map(holding -> new PortfolioValueHistoryService.PositionTrades(
                        holding.getSymbol(),
                        transactions.findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(holding.getId()).stream()
                                .filter(StockTransaction::isSettleToCash)
                                .sorted(Comparator
                                        .comparing(StockTransaction::getOccurredOn)
                                        .thenComparing(StockTransaction::getCreatedAt))
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
        StockPortfolio portfolio = new StockPortfolio(
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
        StockPortfolio portfolio = requireOwned(id);
        portfolio.setName(request.name().trim());
        portfolio.setDescription(Strings.trimToNull(request.description()));
        return toDetail(portfolio);
    }

    @Transactional
    public void delete(UUID id) {
        portfolios.delete(requireOwned(id));
    }

    @Transactional
    public PortfolioDetailResponse seedLazy(UUID portfolioId, LazyStockSeedRequest request) {
        StockPortfolio portfolio = requireOwned(portfolioId);
        ensureCashHolding(portfolio);
        boolean hasNonCash = portfolio.getHoldings().stream().anyMatch(h -> !h.isCash());
        if (hasNonCash) {
            throw new IllegalArgumentException("Lazy seed is only allowed for an empty portfolio");
        }
        for (HoldingCreateRequest item : request.holdings()) {
            if (item.unitPrice() == null) {
                throw new IllegalArgumentException("unitPrice is required for lazy stock seed: " + item.symbol());
            }
            if (item.occurredOn() == null) {
                throw new IllegalArgumentException("occurredOn is required for lazy stock seed: " + item.symbol());
            }
            openHolding(portfolioId, item);
        }
        return toDetail(requireOwned(portfolioId));
    }

    @Transactional
    public HoldingResponse openHolding(UUID portfolioId, HoldingCreateRequest request) {
        StockPortfolio portfolio = requireOwned(portfolioId);
        ensureCashHolding(portfolio);
        CatalogItem instrument = resolveInstrument(request);
        rejectCashSymbol(instrument.symbol());
        if (holdings.existsByPortfolioIdAndSymbol(portfolio.getId(), instrument.symbol())) {
            throw new ConflictException("Holding already exists for symbol: " + instrument.symbol());
        }
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        StockHolding holding = new StockHolding(
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
        applyTrade(holding, instrument, TradeSide.BUY, request.quantity(), occurredOn, request.unitPrice(), request.note());
        return toHolding(holding);
    }

    @Transactional
    public HoldingResponse buy(UUID portfolioId, UUID holdingId, TradeRequest request) {
        StockHolding holding = requireOwnedHolding(portfolioId, holdingId);
        rejectCashHolding(holding);
        CatalogItem instrument = catalog.requireBySymbol(MARKET, holding.getSymbol());
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        applyTrade(holding, instrument, TradeSide.BUY, request.quantity(), occurredOn, request.unitPrice(), request.note());
        return toHolding(holding);
    }

    @Transactional
    public HoldingResponse sell(UUID portfolioId, UUID holdingId, TradeRequest request) {
        StockHolding holding = requireOwnedHolding(portfolioId, holdingId);
        rejectCashHolding(holding);
        CatalogItem instrument = catalog.requireBySymbol(MARKET, holding.getSymbol());
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        if (holding.getQuantity().compareTo(request.quantity()) < 0) {
            throw new IllegalArgumentException("Sell quantity exceeds current holding quantity");
        }
        applyTrade(holding, instrument, TradeSide.SELL, request.quantity(), occurredOn, request.unitPrice(), request.note());
        return toHolding(holding);
    }

    @Transactional
    public HoldingResponse depositCash(UUID portfolioId, CashMovementRequest request) {
        return moveCash(portfolioId, request, TxKind.DEPOSIT);
    }

    @Transactional
    public HoldingResponse withdrawCash(UUID portfolioId, CashMovementRequest request) {
        return moveCash(portfolioId, request, TxKind.WITHDRAW);
    }

    @Transactional
    public HoldingResponse addDividend(UUID portfolioId, CashMovementRequest request) {
        return moveCash(portfolioId, request, TxKind.DIVIDEND);
    }

    @Transactional
    public HoldingResponse addCoupon(UUID portfolioId, CashMovementRequest request) {
        return moveCash(portfolioId, request, TxKind.COUPON);
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> listTransactions(UUID portfolioId, UUID holdingId) {
        requireOwnedHolding(portfolioId, holdingId);
        return transactions.findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(holdingId).stream()
                .map(this::toTrade)
                .toList();
    }

    @Transactional(readOnly = true)
    public PassiveIncomeResponse passiveIncome(UUID portfolioId, Integer year) {
        StockPortfolio portfolio = requireOwned(portfolioId);
        ensureCashHolding(portfolio);
        int selectedYear = year == null ? PassiveIncomeCalc.baseYear() : year;
        int baseYear = PassiveIncomeCalc.baseYear();
        BigDecimal gross = BigDecimal.ZERO;
        for (StockHolding holding : portfolio.getHoldings()) {
            if (holding.isCash() || holding.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            StockInstrument instrument = instruments.findBySymbol(holding.getSymbol()).orElse(null);
            gross = gross.add(PassiveIncomeCalc.grossForHolding(
                    holding.getQuantity(), instrument, selectedYear, baseYear));
        }
        BigDecimal tax = portfolio.getTaxRatePercent();
        BigDecimal net = PassiveIncomeCalc.afterTax(gross, tax);
        BigDecimal monthly = net.divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
        return new PassiveIncomeResponse(
                selectedYear,
                tax,
                PassiveIncomeCalc.money(gross),
                PassiveIncomeCalc.money(net),
                PassiveIncomeCalc.money(monthly),
                marketDataProperties.quoteCurrency(MARKET)
        );
    }

    @Transactional
    public PortfolioSettingsResponse updateSettings(UUID portfolioId, PortfolioSettingsRequest request) {
        StockPortfolio portfolio = requireOwned(portfolioId);
        portfolio.setName(request.name().trim());
        portfolio.setTaxRatePercent(request.taxRatePercent());
        return new PortfolioSettingsResponse(portfolio.getName(), portfolio.getTaxRatePercent());
    }

    @Transactional(readOnly = true)
    public PaymentCalendarResponse paymentCalendar(UUID portfolioId, Integer year) {
        StockPortfolio portfolio = requireOwned(portfolioId);
        ensureCashHolding(portfolio);
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        var provider = quotes.requireProvider(MARKET);
        List<PaymentCalendarResponse.PaymentCalendarItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (StockHolding holding : portfolio.getHoldings()) {
            if (holding.isCash() || holding.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            CatalogItem item = catalog.findBySymbol(MARKET, holding.getSymbol());
            String externalId = item != null ? item.externalId() : holding.getSymbol();
            String displayName = item != null && item.name() != null
                    ? item.name()
                    : (holding.getName() != null ? holding.getName() : holding.getSymbol());
            for (var payment : provider.fetchCorporatePayments(externalId)) {
                if (payment.date() == null || payment.date().getYear() != selectedYear) {
                    continue;
                }
                if (payment.valuePerUnit() == null || payment.valuePerUnit().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal amount = payment.valuePerUnit().multiply(holding.getQuantity());
                total = total.add(amount);
                items.add(new PaymentCalendarResponse.PaymentCalendarItem(
                        holding.getSymbol(),
                        displayName,
                        payment.kind(),
                        payment.date(),
                        PassiveIncomeCalc.money(payment.valuePerUnit()),
                        holding.getQuantity(),
                        PassiveIncomeCalc.money(amount),
                        payment.currency() != null ? payment.currency() : marketDataProperties.quoteCurrency(MARKET)
                ));
            }
        }
        items.sort(Comparator
                .comparing(PaymentCalendarResponse.PaymentCalendarItem::date)
                .thenComparing(PaymentCalendarResponse.PaymentCalendarItem::symbol));
        return new PaymentCalendarResponse(
                selectedYear,
                items,
                PassiveIncomeCalc.money(total),
                marketDataProperties.quoteCurrency(MARKET)
        );
    }

    @Transactional(readOnly = true)
    public HoldingCashflowResponse getCashflow(UUID portfolioId, UUID holdingId) {
        StockHolding holding = requireOwnedHolding(portfolioId, holdingId);
        rejectCashHolding(holding);
        StockInstrument instrument = requireInstrumentForHolding(holding);
        return toCashflow(holding, instrument);
    }

    @Transactional
    public HoldingCashflowResponse updateCashflow(UUID portfolioId, UUID holdingId, HoldingCashflowRequest request) {
        StockHolding holding = requireOwnedHolding(portfolioId, holdingId);
        rejectCashHolding(holding);
        StockInstrument instrument = requireInstrumentForHolding(holding);
        instrument.setAssetKind(request.assetKind() == null ? AssetKind.EQUITY : request.assetKind());
        instrument.setAnnualCashflowPerUnit(request.annualCashflowPerUnit());
        instrument.setCashflowGrowthPct(request.cashflowGrowthPct());
        instrument.setCashflowUntilYear(request.cashflowUntilYear());
        return toCashflow(holding, instrument);
    }

    private StockInstrument requireInstrumentForHolding(StockHolding holding) {
        return instruments.findBySymbol(holding.getSymbol())
                .orElseThrow(() -> new ResourceNotFoundException("StockInstrument", holding.getSymbol()));
    }

    private HoldingCashflowResponse toCashflow(StockHolding holding, StockInstrument instrument) {
        return new HoldingCashflowResponse(
                holding.getId(),
                holding.getSymbol(),
                instrument.getAssetKind(),
                instrument.getAnnualCashflowPerUnit(),
                instrument.getCashflowGrowthPct(),
                instrument.getCashflowUntilYear()
        );
    }

    private HoldingResponse moveCash(UUID portfolioId, CashMovementRequest request, TxKind kind) {
        StockPortfolio portfolio = requireOwned(portfolioId);
        StockHolding cash = ensureCashHolding(portfolio);
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        TradeSide side = kind == TxKind.WITHDRAW ? TradeSide.SELL : TradeSide.BUY;
        BigDecimal unitPrice;
        StockHolding related = null;
        boolean settleToCash = request.settleToCash() == null || request.settleToCash();

        if (kind == TxKind.DEPOSIT || kind == TxKind.WITHDRAW) {
            unitPrice = BigDecimal.ONE;
            settleToCash = true;
            if (kind == TxKind.WITHDRAW && cash.getQuantity().compareTo(request.amount()) < 0) {
                throw new IllegalArgumentException("Withdraw amount exceeds cash balance");
            }
        } else {
            unitPrice = null;
            if (request.relatedHoldingId() == null) {
                throw new IllegalArgumentException("relatedHoldingId is required for " + kind);
            }
            related = requireOwnedHolding(portfolioId, request.relatedHoldingId());
            if (related.isCash()) {
                throw new IllegalArgumentException("relatedHolding must be a non-cash holding");
            }
        }

        applyCashTx(cash, side, kind, related, request.amount(), occurredOn, unitPrice, request.note(), settleToCash);
        return toHolding(cash);
    }

    private void applyCashTx(
            StockHolding cash,
            TradeSide side,
            TxKind kind,
            StockHolding relatedHolding,
            BigDecimal quantity,
            LocalDate occurredOn,
            BigDecimal unitPrice,
            String note,
            boolean settleToCash
    ) {
        // Reinvested / spent income: record on the asset only — never touch cash ledger or qty.
        StockHolding txHolding = !settleToCash && relatedHolding != null ? relatedHolding : cash;
        StockTransaction tx = new StockTransaction(
                UUID.randomUUID(),
                txHolding,
                side,
                kind,
                relatedHolding,
                quantity,
                occurredOn,
                unitPrice,
                Strings.trimToNull(note),
                settleToCash
        );
        transactions.save(tx);
        if (!settleToCash) {
            return;
        }
        if (side == TradeSide.BUY) {
            cash.setQuantity(cash.getQuantity().add(quantity));
        } else {
            cash.setQuantity(cash.getQuantity().subtract(quantity));
        }
    }

    private StockHolding ensureCashHolding(StockPortfolio portfolio) {
        return holdings.findByPortfolioIdAndCashTrue(portfolio.getId())
                .orElseGet(() -> {
                    StockHolding cash = new StockHolding(
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

    private void rejectCashHolding(StockHolding holding) {
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
            StockHolding holding,
            CatalogItem instrument,
            TradeSide side,
            BigDecimal quantity,
            LocalDate occurredOn,
            BigDecimal unitPrice,
            String note
    ) {
        BigDecimal price = quotes.resolveTradePrice(instrument, occurredOn, unitPrice);
        StockTransaction tx = new StockTransaction(
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

    private StockHolding requireOwnedHolding(UUID portfolioId, UUID holdingId) {
        requireOwned(portfolioId);
        return holdings.findByIdAndPortfolioId(holdingId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("StockHolding", holdingId));
    }

    private StockPortfolio requireOwned(UUID id) {
        return portfolios.findByIdAndOwnerId(id, currentUser.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException("StockPortfolio", id));
    }

    private AppUser requireOwner() {
        UUID ownerId = currentUser.requireUserId();
        return users.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("User", ownerId));
    }

    private PortfolioSummaryResponse toSummary(StockPortfolio portfolio) {
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
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }

    private PortfolioDetailResponse toDetail(StockPortfolio portfolio) {
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
                portfolio.getTaxRatePercent(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }

    private List<HoldingResponse> enrichHoldings(StockPortfolio portfolio) {
        return portfolio.getHoldings().stream()
                .sorted(Comparator
                        .comparing(StockHolding::isCash).reversed()
                        .thenComparing((StockHolding h) -> h.getQuantity().compareTo(BigDecimal.ZERO) > 0).reversed()
                        .thenComparing(StockHolding::getSymbol))
                .map(this::toHolding)
                .toList();
    }

    private HoldingResponse toHolding(StockHolding holding) {
        LocalDate openedOn = transactions
                .findFirstByHoldingIdAndSideAndKindOrderByOccurredOnAscCreatedAtAsc(
                        holding.getId(), TradeSide.BUY, TxKind.TRADE)
                .map(StockTransaction::getOccurredOn)
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
                holding.getCreatedAt(),
                holding.getUpdatedAt()
        );
        BigDecimal incomeAbs = incomeAbsFor(holding.getId());
        return HoldingValuation.enrich(base, MARKET, catalog, quotes, tradesFor(holding.getId()), incomeAbs);
    }

    private BigDecimal incomeAbsFor(UUID holdingId) {
        return transactions.findByRelatedHoldingIdAndKindIn(holdingId, INCOME_KINDS).stream()
                .map(StockTransaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<HoldingValuation.TradeInfo> tradesFor(UUID holdingId) {
        return transactions
                .findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(holdingId)
                .stream()
                .filter(StockTransaction::isSettleToCash)
                .sorted(Comparator
                        .comparing(StockTransaction::getOccurredOn)
                        .thenComparing(StockTransaction::getCreatedAt))
                .map(tx -> new HoldingValuation.TradeInfo(
                        tx.getSide(),
                        tx.getKind(),
                        tx.getQuantity(),
                        tx.getUnitPrice(),
                        tx.getOccurredOn()
                ))
                .toList();
    }

    private TradeResponse toTrade(StockTransaction tx) {
        return new TradeResponse(
                tx.getId(),
                tx.getSide(),
                tx.getKind() == null ? TxKind.TRADE : tx.getKind(),
                tx.getQuantity(),
                tx.getOccurredOn(),
                tx.getUnitPrice(),
                tx.getNote(),
                tx.isSettleToCash(),
                tx.getCreatedAt()
        );
    }
}
