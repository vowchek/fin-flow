package com.ledger.application;

import com.ledger.api.dto.HoldingCreateRequest;
import com.ledger.api.dto.HoldingResponse;
import com.ledger.api.dto.PortfolioDetailResponse;
import com.ledger.api.dto.PortfolioRequest;
import com.ledger.api.dto.PortfolioSummaryResponse;
import com.ledger.api.dto.TradeRequest;
import com.ledger.api.dto.TradeResponse;
import com.ledger.domain.AppUser;
import com.ledger.domain.AssetMarket;
import com.ledger.domain.CatalogItem;
import com.ledger.domain.StockHolding;
import com.ledger.domain.StockPortfolio;
import com.ledger.domain.StockTransaction;
import com.ledger.domain.TradeSide;
import com.ledger.infrastructure.marketdata.MarketDataProperties;
import com.ledger.infrastructure.persistence.StockHoldingRepository;
import com.ledger.infrastructure.persistence.StockPortfolioRepository;
import com.ledger.infrastructure.persistence.StockTransactionRepository;
import com.ledger.infrastructure.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class StockPortfolioService {

    private static final AssetMarket MARKET = AssetMarket.MOEX;

    private final StockPortfolioRepository portfolios;
    private final StockHoldingRepository holdings;
    private final StockTransactionRepository transactions;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final CatalogService catalog;
    private final QuoteService quotes;
    private final MarketDataProperties marketDataProperties;

    public StockPortfolioService(
            StockPortfolioRepository portfolios,
            StockHoldingRepository holdings,
            StockTransactionRepository transactions,
            UserRepository users,
            CurrentUserService currentUser,
            CatalogService catalog,
            QuoteService quotes,
            MarketDataProperties marketDataProperties
    ) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.transactions = transactions;
        this.users = users;
        this.currentUser = currentUser;
        this.catalog = catalog;
        this.quotes = quotes;
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
    public PortfolioDetailResponse create(PortfolioRequest request) {
        AppUser owner = requireOwner();
        StockPortfolio portfolio = new StockPortfolio(
                UUID.randomUUID(),
                owner,
                request.name().trim(),
                Strings.trimToNull(request.description())
        );
        return toDetail(portfolios.save(portfolio));
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
    public HoldingResponse openHolding(UUID portfolioId, HoldingCreateRequest request) {
        StockPortfolio portfolio = requireOwned(portfolioId);
        CatalogItem instrument = resolveInstrument(request);
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
        CatalogItem instrument = catalog.requireBySymbol(MARKET, holding.getSymbol());
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        applyTrade(holding, instrument, TradeSide.BUY, request.quantity(), occurredOn, request.unitPrice(), request.note());
        return toHolding(holding);
    }

    @Transactional
    public HoldingResponse sell(UUID portfolioId, UUID holdingId, TradeRequest request) {
        StockHolding holding = requireOwnedHolding(portfolioId, holdingId);
        CatalogItem instrument = catalog.requireBySymbol(MARKET, holding.getSymbol());
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        if (holding.getQuantity().compareTo(request.quantity()) < 0) {
            throw new IllegalArgumentException("Sell quantity exceeds current holding quantity");
        }
        applyTrade(holding, instrument, TradeSide.SELL, request.quantity(), occurredOn, request.unitPrice(), request.note());
        return toHolding(holding);
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> listTransactions(UUID portfolioId, UUID holdingId) {
        requireOwnedHolding(portfolioId, holdingId);
        return transactions.findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(holdingId).stream()
                .map(this::toTrade)
                .toList();
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
        List<HoldingResponse> holdingResponses = enrichHoldings(portfolio);
        long active = holdingResponses.stream()
                .filter(h -> h.quantity().compareTo(BigDecimal.ZERO) > 0)
                .count();
        return PortfolioMetrics.summary(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                (int) active,
                holdingResponses,
                marketDataProperties.quoteCurrency(MARKET),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }

    private PortfolioDetailResponse toDetail(StockPortfolio portfolio) {
        List<HoldingResponse> holdings = enrichHoldings(portfolio);
        return PortfolioMetrics.detail(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                holdings,
                List.of(),
                marketDataProperties.quoteCurrency(MARKET),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }

    private List<HoldingResponse> enrichHoldings(StockPortfolio portfolio) {
        return portfolio.getHoldings().stream()
                .sorted(Comparator
                        .comparing((StockHolding h) -> h.getQuantity().compareTo(BigDecimal.ZERO) > 0).reversed()
                        .thenComparing(StockHolding::getSymbol))
                .map(this::toHolding)
                .toList();
    }

    private HoldingResponse toHolding(StockHolding holding) {
        LocalDate openedOn = transactions
                .findFirstByHoldingIdAndSideOrderByOccurredOnAscCreatedAtAsc(holding.getId(), TradeSide.BUY)
                .map(StockTransaction::getOccurredOn)
                .orElse(null);
        HoldingResponse base = new HoldingResponse(
                holding.getId(),
                holding.getSymbol(),
                holding.getName(),
                holding.getQuantity(),
                openedOn,
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
        return HoldingValuation.enrich(base, MARKET, catalog, quotes, tradesFor(holding.getId()));
    }

    private List<HoldingValuation.TradeInfo> tradesFor(UUID holdingId) {
        return transactions
                .findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(holdingId)
                .stream()
                .sorted(Comparator
                        .comparing(StockTransaction::getOccurredOn)
                        .thenComparing(StockTransaction::getCreatedAt))
                .map(tx -> new HoldingValuation.TradeInfo(tx.getSide(), tx.getQuantity(), tx.getUnitPrice(), tx.getOccurredOn()))
                .toList();
    }

    private TradeResponse toTrade(StockTransaction tx) {
        return new TradeResponse(
                tx.getId(),
                tx.getSide(),
                tx.getQuantity(),
                tx.getOccurredOn(),
                tx.getUnitPrice(),
                tx.getNote(),
                tx.getCreatedAt()
        );
    }
}


