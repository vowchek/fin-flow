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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
        if (item == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(getLiveMany(List.of(item)).get(item.id()));
    }

    /**
     * Batches remote fetches per market and reuses TTL cache.
     */
    @Transactional
    public Map<UUID, LiveQuote> getLiveMany(Collection<CatalogItem> items) {
        Map<UUID, LiveQuote> result = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        Instant now = Instant.now();
        Map<AssetMarket, List<CatalogItem>> needFetch = new HashMap<>();
        Map<UUID, PriceQuote> staleById = new HashMap<>();

        for (CatalogItem item : items) {
            if (item == null || result.containsKey(item.id())) {
                continue;
            }
            String expectedCurrency = properties.quoteCurrency(item.market());
            var key = new PriceQuote.QuoteId(item.market(), item.id());
            Optional<PriceQuote> cached = quotes.findById(key);
            if (cached.isPresent()
                    && cached.get().getFetchedAt().plus(properties.getQuoteTtl()).isAfter(now)
                    && expectedCurrency.equalsIgnoreCase(cached.get().getCurrency())
                    && cached.get().getPreviousClose() != null) {
                PriceQuote q = cached.get();
                result.put(item.id(), new LiveQuote(q.getPrice(), q.getPreviousClose(), q.getCurrency(), q.getFetchedAt()));
                continue;
            }
            cached.ifPresent(q -> staleById.put(item.id(), q));
            needFetch.computeIfAbsent(item.market(), ignored -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<AssetMarket, List<CatalogItem>> entry : needFetch.entrySet()) {
            AssetMarket market = entry.getKey();
            List<CatalogItem> batch = entry.getValue();
            MarketDataProvider provider = requireProvider(market);
            List<String> externalIds = batch.stream().map(CatalogItem::externalId).distinct().toList();
            Map<String, MarketDataProvider.RemoteQuote> remote = provider.fetchLiveMany(externalIds);
            String expectedCurrency = properties.quoteCurrency(market);

            for (CatalogItem item : batch) {
                MarketDataProvider.RemoteQuote rq = remote.get(item.externalId());
                if (rq != null) {
                    PriceQuote entity = quotes.findById(new PriceQuote.QuoteId(item.market(), item.id()))
                            .orElseGet(() -> new PriceQuote(
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
                    result.put(item.id(), new LiveQuote(rq.price(), rq.previousClose(), rq.currency(), now));
                    continue;
                }
                PriceQuote stale = staleById.get(item.id());
                if (stale != null && expectedCurrency.equalsIgnoreCase(stale.getCurrency())) {
                    result.put(item.id(), new LiveQuote(
                            stale.getPrice(),
                            stale.getPreviousClose(),
                            stale.getCurrency(),
                            stale.getFetchedAt()
                    ));
                }
            }
        }
        return result;
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
    public void ensureHistory(CatalogItem item, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return;
        }
        String expectedCurrency = properties.quoteCurrency(item.market());
        List<PriceHistory> existing = history.findByMarketAndInstrumentIdAndPriceDateBetweenOrderByPriceDateAsc(
                item.market(),
                item.id(),
                from,
                to
        );
        Set<LocalDate> known = existing.stream()
                .filter(row -> expectedCurrency.equalsIgnoreCase(row.getCurrency()))
                .map(PriceHistory::getPriceDate)
                .collect(Collectors.toCollection(HashSet::new));

        MarketDataProvider provider = requireProvider(item.market());
        List<MarketDataProvider.HistoricalPrice> remote = provider.fetchHistoricalRange(item.externalId(), from, to);
        for (MarketDataProvider.HistoricalPrice point : remote) {
            if (known.contains(point.date())) {
                continue;
            }
            history.save(new PriceHistory(
                    UUID.randomUUID(),
                    item.market(),
                    item.id(),
                    point.date(),
                    point.price(),
                    expectedCurrency
            ));
            known.add(point.date());
        }
    }

    @Transactional(readOnly = true)
    public NavigableMap<LocalDate, BigDecimal> priceSeries(CatalogItem item, LocalDate from, LocalDate to) {
        String expectedCurrency = properties.quoteCurrency(item.market());
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        history.findByMarketAndInstrumentIdAndPriceDateBetweenOrderByPriceDateAsc(
                        item.market(),
                        item.id(),
                        from,
                        to
                )
                .stream()
                .filter(row -> expectedCurrency.equalsIgnoreCase(row.getCurrency()))
                .forEach(row -> series.put(row.getPriceDate(), row.getPrice()));
        return series;
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
