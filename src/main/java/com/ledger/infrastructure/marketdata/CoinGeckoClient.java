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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Profile("!test")
public class CoinGeckoClient implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);
    private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

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
        try {
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/simple/price")
                            .queryParam("ids", externalId)
                            .queryParam("vs_currencies", currency)
                            .queryParam("include_24hr_change", "true")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null || !root.has(externalId)) {
                return Optional.empty();
            }
            JsonNode node = root.get(externalId);
            if (!node.has(currency)) {
                return Optional.empty();
            }
            BigDecimal price = node.get(currency).decimalValue();
            BigDecimal previous = null;
            String changeKey = currency + "_24h_change";
            if (node.has(changeKey) && !node.get(changeKey).isNull()) {
                BigDecimal changePct = node.get(changeKey).decimalValue();
                // price = previous * (1 + change/100) => previous = price / (1 + change/100)
                BigDecimal factor = BigDecimal.ONE.add(changePct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
                if (factor.compareTo(BigDecimal.ZERO) != 0) {
                    previous = price.divide(factor, 8, RoundingMode.HALF_UP);
                }
            }
            return Optional.of(new RemoteQuote(price, previous, currency.toUpperCase(Locale.ROOT)));
        } catch (RestClientException ex) {
            log.warn("CoinGecko live quote failed for {}: {}", externalId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<BigDecimal> fetchHistorical(String externalId, LocalDate date) {
        try {
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/coins/{id}/history")
                            .queryParam("date", date.format(HISTORY_DATE))
                            .queryParam("localization", "false")
                            .build(externalId))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return Optional.empty();
            }
            JsonNode priceNode = root.path("market_data").path("current_price").path(currency);
            if (priceNode.isMissingNode() || priceNode.isNull()) {
                return Optional.empty();
            }
            return Optional.of(priceNode.decimalValue());
        } catch (RestClientException ex) {
            log.warn("CoinGecko history failed for {} {}: {}", externalId, date, ex.getMessage());
            return Optional.empty();
        }
    }
}
