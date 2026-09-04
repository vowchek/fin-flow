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
import com.ledger.domain.CryptoHolding;
import com.ledger.domain.CryptoPortfolio;
import com.ledger.domain.CryptoTransaction;
import com.ledger.domain.CatalogItem;
import com.ledger.domain.TradeSide;
import com.ledger.infrastructure.marketdata.MarketDataProperties;
import com.ledger.infrastructure.persistence.CryptoHoldingRepository;
import com.ledger.infrastructure.persistence.CryptoPortfolioRepository;
import com.ledger.infrastructure.persistence.CryptoTransactionRepository;
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
public class CryptoPortfolioService {

    private static final AssetMarket MARKET = AssetMarket.CRYPTO;

    private final CryptoPortfolioRepository portfolios;
    private final CryptoHoldingRepository holdings;
    private final CryptoTransactionRepository transactions;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final CatalogService catalog;
    private final QuoteService quotes;
    private final MarketDataProperties marketDataProperties;

    public CryptoPortfolioService(
            CryptoPortfolioRepository portfolios,
            CryptoHoldingRepository holdings,
            CryptoTransactionRepository transactions,
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
        CryptoPortfolio portfolio = new CryptoPortfolio(
                UUID.randomUUID(),
                owner,
                request.name().trim(),
                Strings.trimToNull(request.description())
        );
        return toDetail(portfolios.save(portfolio));
    }

    @Transactional
    public PortfolioDetailResponse update(UUID id, PortfolioRequest request) {
        CryptoPortfolio portfolio = requireOwned(id);
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
        CryptoPortfolio portfolio = requireOwned(portfolioId);
        CatalogItem instrument = resolveInstrument(request);
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
        applyTrade(holding, instrument, TradeSide.BUY, request.quantity(), occurredOn, request.unitPrice(), request.note());
        return toHolding(holding);
    }

    @Transactional
    public HoldingResponse buy(UUID portfolioId, UUID holdingId, TradeRequest request) {
        CryptoHolding holding = requireOwnedHolding(portfolioId, holdingId);
        CatalogItem instrument = catalog.requireBySymbol(MARKET, holding.getSymbol());
        LocalDate occurredOn = request.occurredOn() == null ? LocalDate.now() : request.occurredOn();
        applyTrade(holding, instrument, TradeSide.BUY, request.quantity(), occurredOn, request.unitPrice(), request.note());
        return toHolding(holding);
    }

    @Transactional
    public HoldingResponse sell(UUID portfolioId, UUID holdingId, TradeRequest request) {
        CryptoHolding holding = requireOwnedHolding(portfolioId, holdingId);
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
            CryptoHolding holding,
            CatalogItem instrument,
            TradeSide side,
            BigDecimal quantity,
            LocalDate occurredOn,
            BigDecimal unitPrice,
            String note
    ) {
        BigDecimal price = quotes.resolveTradePrice(instrument, occurredOn, unitPrice);
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

    private PortfolioDetailResponse toDetail(CryptoPortfolio portfolio) {
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

    private List<HoldingResponse> enrichHoldings(CryptoPortfolio portfolio) {
        return portfolio.getHoldings().stream()
                .sorted(Comparator
                        .comparing((CryptoHolding h) -> h.getQuantity().compareTo(BigDecimal.ZERO) > 0).reversed()
                        .thenComparing(CryptoHolding::getSymbol))
                .map(this::toHolding)
                .toList();
    }

    private HoldingResponse toHolding(CryptoHolding holding) {
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
                        .comparing(CryptoTransaction::getOccurredOn)
                        .thenComparing(CryptoTransaction::getCreatedAt))
                .map(tx -> new HoldingValuation.TradeInfo(tx.getSide(), tx.getQuantity(), tx.getUnitPrice(), tx.getOccurredOn()))
                .toList();
    }

    private TradeResponse toTrade(CryptoTransaction tx) {
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


