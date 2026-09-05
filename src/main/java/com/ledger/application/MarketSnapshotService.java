package com.ledger.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledger.api.dto.MarketStripItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Profile("!test")
public class MarketSnapshotService implements MarketSnapshots {

    private static final Logger log = LoggerFactory.getLogger(MarketSnapshotService.class);
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final RestClient moex;
    private final RestClient coinGecko;
    private final RestClient yahoo;

    private final AtomicReference<CacheEntry> stockCache = new AtomicReference<>();
    private final AtomicReference<CacheEntry> cryptoCache = new AtomicReference<>();

    public MarketSnapshotService(
            @Qualifier("moexRestClient") RestClient moex,
            @Qualifier("coinGeckoRestClient") RestClient coinGecko,
            @Qualifier("yahooRestClient") RestClient yahoo
    ) {
        this.moex = moex;
        this.coinGecko = coinGecko;
        this.yahoo = yahoo;
    }

    @Override
    public List<MarketStripItemResponse> stockStrip() {
        return cached(stockCache, this::loadStockStrip);
    }

    @Override
    public List<MarketStripItemResponse> cryptoStrip() {
        return cached(cryptoCache, this::loadCryptoStrip);
    }

    private List<MarketStripItemResponse> loadStockStrip() {
        List<MarketStripItemResponse> out = new ArrayList<>();
        moexIndex("IMOEX", "IMOEX", "").ifPresent(out::add);
        moexCurrency("USD000UTSTOM", "USD", "₽").ifPresent(out::add);
        yahooQuote("^GSPC", "sp500", "S&P", "$", "").ifPresent(out::add);
        yahooQuote("^IXIC", "nasdaq", "NDX", "$", "").ifPresent(out::add);
        moexCurrency("GLDRUB_TOM", "Au", "₽").ifPresent(out::add);
        moexFutures("BR", "Brent", "$").ifPresent(out::add);
        return out;
    }

    private List<MarketStripItemResponse> loadCryptoStrip() {
        List<MarketStripItemResponse> out = new ArrayList<>();
        try {
            JsonNode prices = coinGecko.get()
                    .uri(uri -> uri.path("/simple/price")
                            .queryParam("ids", "bitcoin,ethereum")
                            .queryParam("vs_currencies", "usd")
                            .queryParam("include_24hr_change", "true")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (prices != null) {
                coinPrice(prices, "bitcoin", "btc", "BTC", "$").ifPresent(out::add);
                coinPrice(prices, "ethereum", "eth", "ETH", "$").ifPresent(out::add);
            }
        } catch (RestClientException ex) {
            log.warn("CoinGecko prices for strip failed: {}", ex.getMessage());
        }
        try {
            JsonNode global = coinGecko.get()
                    .uri(uri -> uri.path("/global").build())
                    .retrieve()
                    .body(JsonNode.class);
            if (global != null) {
                JsonNode data = global.path("data");
                BigDecimal dominance = decimal(data.path("market_cap_percentage").path("btc"));
                if (dominance != null) {
                    out.add(new MarketStripItemResponse(
                            "btc-dom",
                            "Dom",
                            dominance.setScale(2, RoundingMode.HALF_UP),
                            null,
                            "",
                            "%"
                    ));
                }
                BigDecimal mcap = decimal(data.path("total_market_cap").path("usd"));
                BigDecimal mcapChange = decimal(data.path("market_cap_change_percentage_24h_usd"));
                if (mcap != null) {
                    out.add(new MarketStripItemResponse(
                            "crypto-mcap",
                            "Cap",
                            mcap,
                            mcapChange,
                            "$",
                            ""
                    ));
                }
            }
        } catch (RestClientException ex) {
            log.warn("CoinGecko global for strip failed: {}", ex.getMessage());
        }
        return out;
    }

    private java.util.Optional<MarketStripItemResponse> coinPrice(
            JsonNode root,
            String id,
            String itemId,
            String label,
            String prefix
    ) {
        JsonNode node = root.path(id);
        BigDecimal price = decimal(node.path("usd"));
        if (price == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new MarketStripItemResponse(
                itemId,
                label,
                price,
                decimal(node.path("usd_24h_change")),
                prefix,
                ""
        ));
    }

    private java.util.Optional<MarketStripItemResponse> moexIndex(String secid, String label, String suffix) {
        try {
            JsonNode root = moex.get()
                    .uri(uri -> uri.path("/iss/engines/stock/markets/index/securities/{secid}.json")
                            .queryParam("iss.meta", "off")
                            .queryParam("iss.only", "marketdata")
                            .build(secid))
                    .retrieve()
                    .body(JsonNode.class);
            return fromMoexMarketdata(root, secid.toLowerCase(), label, "", suffix, "CURRENTVALUE", "LASTCHANGEPRCNT");
        } catch (RestClientException ex) {
            log.warn("MOEX index {} failed: {}", secid, ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<MarketStripItemResponse> moexCurrency(String secid, String label, String suffix) {
        try {
            JsonNode root = moex.get()
                    .uri(uri -> uri.path("/iss/engines/currency/markets/selt/securities/{secid}.json")
                            .queryParam("iss.meta", "off")
                            .queryParam("iss.only", "marketdata")
                            .build(secid))
                    .retrieve()
                    .body(JsonNode.class);
            return fromMoexMarketdata(root, secid.toLowerCase(), label, "", suffix, "LAST", "LASTCHANGEPRCNT");
        } catch (RestClientException ex) {
            log.warn("MOEX FX {} failed: {}", secid, ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<MarketStripItemResponse> moexFutures(String assetCode, String label, String prefix) {
        try {
            JsonNode root = moex.get()
                    .uri(uri -> uri.path("/iss/engines/futures/markets/forts/securities.json")
                            .queryParam("iss.meta", "off")
                            .queryParam("iss.only", "securities,marketdata")
                            .queryParam("assetcode", assetCode)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return java.util.Optional.empty();
            }
            JsonNode securities = root.path("securities");
            JsonNode marketdata = root.path("marketdata");
            int secidIdx = columnIndex(securities.path("columns"), "SECID");
            int lastIdx = columnIndex(marketdata.path("columns"), "LAST");
            int changeIdx = columnIndex(marketdata.path("columns"), "LASTCHANGEPRCNT");
            if (secidIdx < 0 || lastIdx < 0) {
                return java.util.Optional.empty();
            }
            String bestSec = null;
            BigDecimal bestLast = null;
            BigDecimal bestChange = null;
            for (int i = 0; i < marketdata.path("data").size(); i++) {
                JsonNode mrow = marketdata.path("data").get(i);
                BigDecimal last = decimalAt(mrow, lastIdx);
                if (last == null) {
                    continue;
                }
                String secid = textAt(securities.path("data").get(i), secidIdx);
                if (secid == null) {
                    continue;
                }
                if (bestLast == null || last.compareTo(bestLast) > 0) {
                    // Prefer nearer contract by picking first with LAST; fall through to first valid.
                    bestSec = secid;
                    bestLast = last;
                    bestChange = changeIdx >= 0 ? decimalAt(mrow, changeIdx) : null;
                    break;
                }
            }
            if (bestLast == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new MarketStripItemResponse(
                    "brent",
                    label,
                    bestLast,
                    bestChange,
                    prefix,
                    ""
            ));
        } catch (RestClientException ex) {
            log.warn("MOEX futures {} failed: {}", assetCode, ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<MarketStripItemResponse> fromMoexMarketdata(
            JsonNode root,
            String id,
            String label,
            String prefix,
            String suffix,
            String valueCol,
            String changeCol
    ) {
        if (root == null) {
            return java.util.Optional.empty();
        }
        JsonNode marketdata = root.path("marketdata");
        int valueIdx = columnIndex(marketdata.path("columns"), valueCol);
        int changeIdx = columnIndex(marketdata.path("columns"), changeCol);
        if (valueIdx < 0 || marketdata.path("data").isEmpty()) {
            return java.util.Optional.empty();
        }
        JsonNode row = marketdata.path("data").get(0);
        BigDecimal value = decimalAt(row, valueIdx);
        if (value == null) {
            return java.util.Optional.empty();
        }
        BigDecimal change = changeIdx >= 0 ? decimalAt(row, changeIdx) : null;
        return java.util.Optional.of(new MarketStripItemResponse(id, label, value, change, prefix, suffix));
    }

    private java.util.Optional<MarketStripItemResponse> yahooQuote(
            String symbol,
            String id,
            String label,
            String prefix,
            String suffix
    ) {
        try {
            JsonNode root = yahoo.get()
                    .uri(uri -> uri.path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "1d")
                            .queryParam("range", "5d")
                            .build(symbol))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return java.util.Optional.empty();
            }
            JsonNode meta = root.path("chart").path("result").path(0).path("meta");
            BigDecimal price = decimal(meta.path("regularMarketPrice"));
            BigDecimal previous = decimal(meta.path("chartPreviousClose"));
            if (price == null && previous == null) {
                previous = decimal(meta.path("previousClose"));
            }
            if (price == null) {
                return java.util.Optional.empty();
            }
            BigDecimal changePct = null;
            if (previous != null && previous.compareTo(BigDecimal.ZERO) > 0) {
                changePct = price.subtract(previous)
                        .divide(previous, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
            return java.util.Optional.of(new MarketStripItemResponse(id, label, price, changePct, prefix, suffix));
        } catch (RestClientException ex) {
            log.warn("Yahoo quote {} failed: {}", symbol, ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    private List<MarketStripItemResponse> cached(
            AtomicReference<CacheEntry> cache,
            java.util.function.Supplier<List<MarketStripItemResponse>> loader
    ) {
        CacheEntry current = cache.get();
        long now = Instant.now().toEpochMilli();
        if (current != null && now - current.fetchedAt < CACHE_TTL_MS) {
            return current.items;
        }
        List<MarketStripItemResponse> loaded = loader.get();
        cache.set(new CacheEntry(now, List.copyOf(loaded)));
        return loaded;
    }

    private static int columnIndex(JsonNode columns, String name) {
        if (columns == null || !columns.isArray()) {
            return -1;
        }
        for (int i = 0; i < columns.size(); i++) {
            if (name.equalsIgnoreCase(columns.get(i).asText())) {
                return i;
            }
        }
        return -1;
    }

    private static String textAt(JsonNode row, int idx) {
        if (row == null || !row.isArray() || idx < 0 || idx >= row.size() || row.get(idx).isNull()) {
            return null;
        }
        String value = row.get(idx).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal decimalAt(JsonNode row, int idx) {
        if (row == null || !row.isArray() || idx < 0 || idx >= row.size()) {
            return null;
        }
        return decimal(row.get(idx));
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return node.decimalValue();
        } catch (Exception ignored) {
            try {
                return new BigDecimal(node.asText());
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private record CacheEntry(long fetchedAt, List<MarketStripItemResponse> items) {
    }
}
