package com.ledger.infrastructure.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledger.domain.AssetMarket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@Profile("!test")
public class CoinGeckoClient implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);

    private final RestClient client;
    private final String currency;

    public CoinGeckoClient(
            @Qualifier("coinGeckoRestClient") RestClient client,
            MarketDataProperties properties
    ) {
        this.client = client;
        this.currency = properties.quoteCurrency(AssetMarket.CRYPTO).toLowerCase(Locale.ROOT);
    }

    @Override
    public AssetMarket market() {
        return AssetMarket.CRYPTO;
    }

    @Override
    public List<RemoteInstrument> search(String query) {
        try {
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/search").queryParam("query", query).build())
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return List.of();
            }
            List<RemoteInstrument> out = new ArrayList<>();
            for (JsonNode coin : root.path("coins")) {
                String id = coin.path("id").asText(null);
                String symbol = coin.path("symbol").asText(null);
                String name = coin.path("name").asText(null);
                if (id == null || symbol == null || name == null) {
                    continue;
                }
                String logo = coin.path("large").asText(null);
                if (logo == null || logo.isBlank()) {
                    logo = coin.path("thumb").asText(null);
                }
                out.add(new RemoteInstrument(
                        symbol.toUpperCase(Locale.ROOT),
                        id,
                        name,
                        logo,
                        currency.toUpperCase(Locale.ROOT)
                ));
                if (out.size() >= 25) {
                    break;
                }
            }
            return out;
        } catch (RestClientException ex) {
            log.warn("CoinGecko search failed for '{}': {}", query, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<RemoteQuote> fetchLive(String externalId) {
        return Optional.ofNullable(fetchLiveMany(List.of(externalId)).get(externalId));
    }

    @Override
    public Map<String, RemoteQuote> fetchLiveMany(Collection<String> externalIds) {
        List<String> ids = externalIds == null
                ? List.of()
                : externalIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String joined = String.join(",", ids);
        try {
            JsonNode root = requestLive(joined);
            return parseLive(root, ids);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                log.warn("CoinGecko rate limited on live quotes, retrying once");
                try {
                    Thread.sleep(1600);
                    JsonNode root = requestLive(joined);
                    return parseLive(root, ids);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Map.of();
                } catch (RestClientException retryEx) {
                    log.warn("CoinGecko live quote retry failed for {}: {}", joined, retryEx.getMessage());
                    return Map.of();
                }
            }
            log.warn("CoinGecko live quote failed for {}: {}", joined, ex.getMessage());
            return Map.of();
        } catch (RestClientException ex) {
            log.warn("CoinGecko live quote failed for {}: {}", joined, ex.getMessage());
            return Map.of();
        }
    }

    private JsonNode requestLive(String joinedIds) {
        return client.get()
                .uri(uri -> uri.path("/simple/price")
                        .queryParam("ids", joinedIds)
                        .queryParam("vs_currencies", currency)
                        .queryParam("include_24hr_change", "true")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private Map<String, RemoteQuote> parseLive(JsonNode root, List<String> ids) {
        Map<String, RemoteQuote> out = new LinkedHashMap<>();
        if (root == null) {
            return out;
        }
        for (String externalId : ids) {
            if (!root.has(externalId)) {
                continue;
            }
            JsonNode node = root.get(externalId);
            if (!node.has(currency)) {
                continue;
            }
            BigDecimal price = node.get(currency).decimalValue();
            BigDecimal previous = null;
            String changeKey = currency + "_24h_change";
            if (node.has(changeKey) && !node.get(changeKey).isNull()) {
                BigDecimal changePct = node.get(changeKey).decimalValue();
                BigDecimal factor = BigDecimal.ONE.add(changePct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
                if (factor.compareTo(BigDecimal.ZERO) != 0) {
                    previous = price.divide(factor, 8, RoundingMode.HALF_UP);
                }
            }
            out.put(externalId, new RemoteQuote(price, previous, currency.toUpperCase(Locale.ROOT)));
        }
        return out;
    }

    @Override
    public Optional<BigDecimal> fetchHistorical(String externalId, LocalDate date) {
        List<HistoricalPrice> rows = fetchHistoricalRange(externalId, date, date);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().price());
    }

    @Override
    public List<HistoricalPrice> fetchHistoricalRange(String externalId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        try {
            long fromTs = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            long toTs = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - 1;
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/coins/{id}/market_chart/range")
                            .queryParam("vs_currency", currency)
                            .queryParam("from", fromTs)
                            .queryParam("to", toTs)
                            .build(externalId))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return List.of();
            }
            // Keep last price per calendar day (UTC).
            Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
            for (JsonNode point : root.path("prices")) {
                if (!point.isArray() || point.size() < 2 || point.get(0).isNull() || point.get(1).isNull()) {
                    continue;
                }
                long ms = point.get(0).asLong();
                LocalDate day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate();
                if (day.isBefore(from) || day.isAfter(to)) {
                    continue;
                }
                byDay.put(day, point.get(1).decimalValue());
            }
            List<HistoricalPrice> out = new ArrayList<>(byDay.size());
            byDay.forEach((day, price) -> out.add(new HistoricalPrice(day, price)));
            return out;
        } catch (RestClientException ex) {
            log.warn("CoinGecko history range failed for {} {}..{}: {}", externalId, from, to, ex.getMessage());
            return List.of();
        }
    }
}
