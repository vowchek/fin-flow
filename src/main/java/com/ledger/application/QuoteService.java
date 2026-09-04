package com.ledger.application;

import com.ledger.domain.AssetMarket;
import com.ledger.domain.CatalogItem;
import com.ledger.domain.PriceHistory;
import com.ledger.domain.PriceQuote;
import com.ledger.infrastructure.marketdata.MarketDataProperties;
import com.ledger.infrastructure.marketdata.MarketDataProvider;
import com.ledger.infrastructure.persistence.PriceHistoryRepository;
import com.ledger.infrastructure.persistence.PriceQuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuoteService {

    private final PriceQuoteRepository quotes;
    private final PriceHistoryRepository history;
    private final CatalogService catalog;
    private final Map<AssetMarket, MarketDataProvider> providers;
    private final MarketDataProperties properties;

    public QuoteService(
            PriceQuoteRepository quotes,
            PriceHistoryRepository history,
            CatalogService catalog,
            List<MarketDataProvider> providers,
            MarketDataProperties properties
    ) {
        this.quotes = quotes;
        this.history = history;
        this.catalog = catalog;
        this.providers = providers.stream()
                .collect(Collectors.toMap(MarketDataProvider::market, Function.identity()));
        this.properties = properties;
    }

    @Transactional
    public Optional<LiveQuote> getLive(CatalogItem item) {
        Instant now = Instant.now();
        var key = new PriceQuote.QuoteId(item.market(), item.id());
        String expectedCurrency = properties.quoteCurrency(item.market());
        Optional<PriceQuote> cached = quotes.findById(key);
        if (cached.isPresent()
                && cached.get().getFetchedAt().plus(properties.getQuoteTtl()).isAfter(now)
                && expectedCurrency.equalsIgnoreCase(cached.get().getCurrency())) {
            PriceQuote q = cached.get();
            return Optional.of(new LiveQuote(q.getPrice(), q.getPreviousClose(), q.getCurrency(), q.getFetchedAt()));
        }
        MarketDataProvider provider = requireProvider(item.market());
        Optional<MarketDataProvider.RemoteQuote> remote = provider.fetchLive(item.externalId());
        if (remote.isEmpty()) {
            return cached.map(q -> new LiveQuote(q.getPrice(), q.getPreviousClose(), q.getCurrency(), q.getFetchedAt()));
        }
        MarketDataProvider.RemoteQuote rq = remote.get();
        PriceQuote entity = cached.orElseGet(() -> new PriceQuote(
                item.market(),
                item.id(),
                rq.price(),
                rq.previousClose(),
                rq.currency(),
                now
        ));
        entity.setPrice(rq.price());
        entity.setPreviousClose(rq.previousClose());
        entity.setCurrency(rq.currency());
        entity.setFetchedAt(now);
        quotes.save(entity);
        return Optional.of(new LiveQuote(rq.price(), rq.previousClose(), rq.currency(), now));
    }

    @Transactional
    public Optional<BigDecimal> getHistorical(CatalogItem item, LocalDate date) {
        String expectedCurrency = properties.quoteCurrency(item.market());
        Optional<PriceHistory> cached = history.findByMarketAndInstrumentIdAndPriceDate(item.market(), item.id(), date);
        if (cached.isPresent() && expectedCurrency.equalsIgnoreCase(cached.get().getCurrency())) {
            return Optional.of(cached.get().getPrice());
        }
        MarketDataProvider provider = requireProvider(item.market());
        Optional<BigDecimal> remote = provider.fetchHistorical(item.externalId(), date);
        remote.ifPresent(price -> {
            cached.ifPresent(history::delete);
            history.save(new PriceHistory(
                    UUID.randomUUID(),
                    item.market(),
                    item.id(),
                    date,
                    price,
                    expectedCurrency
            ));
        });
        return remote;
    }

    @Transactional
    public BigDecimal resolveTradePrice(CatalogItem item, LocalDate date, BigDecimal provided) {
        if (provided != null) {
            return provided;
        }
        return getHistorical(item, date)
                .or(() -> getLive(item).map(LiveQuote::price))
                .orElse(null);
    }

    public MarketDataProvider requireProvider(AssetMarket market) {
        MarketDataProvider provider = providers.get(market);
        if (provider == null) {
            throw new IllegalStateException("No market data provider for " + market);
        }
        return provider;
    }

    public record LiveQuote(
            BigDecimal price,
            BigDecimal previousClose,
            String currency,
            Instant asOf
    ) {
    }
}
