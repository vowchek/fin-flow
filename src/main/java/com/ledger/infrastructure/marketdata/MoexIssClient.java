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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Profile("!test")
public class MoexIssClient implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(MoexIssClient.class);

    private final RestClient client;
    private final String currency;

    public MoexIssClient(
            @Qualifier("moexRestClient") RestClient client,
            MarketDataProperties properties
    ) {
        this.client = client;
        this.currency = properties.getQuoteCurrency();
    }

    @Override
    public AssetMarket market() {
        return AssetMarket.MOEX;
    }

    @Override
    public List<RemoteInstrument> search(String query) {
        try {
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/iss/securities.json")
                            .queryParam("q", query)
                            .queryParam("iss.meta", "off")
                            .queryParam("limit", 25)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return List.of();
            }
            JsonNode securities = root.path("securities");
            JsonNode columns = securities.path("columns");
            JsonNode data = securities.path("data");
            int secidIdx = indexOf(columns, "secid");
            int nameIdx = indexOf(columns, "name");
            int shortIdx = indexOf(columns, "shortname");
            int primaryIdx = indexOf(columns, "primary_boardid");
            int isTradedIdx = indexOf(columns, "is_traded");
            List<RemoteInstrument> out = new ArrayList<>();
            for (JsonNode row : data) {
                if (!row.isArray()) {
                    continue;
                }
                if (isTradedIdx >= 0 && row.size() > isTradedIdx && row.get(isTradedIdx).asInt(1) == 0) {
                    continue;
                }
                if (primaryIdx >= 0 && row.size() > primaryIdx) {
                    String board = row.get(primaryIdx).asText("");
                    if (!board.isBlank() && !"TQBR".equalsIgnoreCase(board) && !"TQTF".equalsIgnoreCase(board)) {
                        // keep shares/ETFs primarily; still allow if TQBR missing for search breadth
                    }
                }
                String secid = textAt(row, secidIdx);
                if (secid == null || secid.isBlank()) {
                    continue;
                }
                String name = textAt(row, shortIdx);
                if (name == null || name.isBlank()) {
                    name = textAt(row, nameIdx);
                }
                if (name == null || name.isBlank()) {
                    name = secid;
                }
                out.add(new RemoteInstrument(
                        secid.toUpperCase(Locale.ROOT),
                        secid.toUpperCase(Locale.ROOT),
                        name,
                        null,
                        currency
                ));
            }
            return out;
        } catch (RestClientException ex) {
            log.warn("MOEX search failed for '{}': {}", query, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<RemoteQuote> fetchLive(String externalId) {
        String secid = externalId.toUpperCase(Locale.ROOT);
        try {
            JsonNode root = client.get()
                    .uri("/iss/engines/stock/markets/shares/securities/{secid}.json?iss.meta=off", secid)
                    .retrieve()
                    .body(JsonNode.class);
            Optional<RemoteQuote> fromMarket = parseMarketdata(root);
            if (fromMarket.isPresent()) {
                return fromMarket;
            }
            return parseSecurities(root);
        } catch (RestClientException ex) {
            log.warn("MOEX live quote failed for {}: {}", secid, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<BigDecimal> fetchHistorical(String externalId, LocalDate date) {
        String secid = externalId.toUpperCase(Locale.ROOT);
        try {
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/iss/history/engines/stock/markets/shares/boards/TQBR/securities/{secid}.json")
                            .queryParam("from", date.toString())
                            .queryParam("till", date.toString())
                            .queryParam("iss.meta", "off")
                            .build(secid))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return Optional.empty();
            }
            JsonNode history = root.path("history");
            int closeIdx = indexOf(history.path("columns"), "CLOSE");
            JsonNode data = history.path("data");
            if (!data.isArray() || data.isEmpty() || closeIdx < 0) {
                return Optional.empty();
            }
            JsonNode row = data.get(0);
            if (!row.isArray() || row.size() <= closeIdx || row.get(closeIdx).isNull()) {
                return Optional.empty();
            }
            return Optional.of(new BigDecimal(row.get(closeIdx).asText()));
        } catch (RestClientException ex) {
            log.warn("MOEX history failed for {} {}: {}", secid, date, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RemoteQuote> parseMarketdata(JsonNode root) {
        if (root == null) {
            return Optional.empty();
        }
        JsonNode marketdata = root.path("marketdata");
        int lastIdx = indexOf(marketdata.path("columns"), "LAST");
        int prevIdx = indexOf(marketdata.path("columns"), "PREVPRICE");
        JsonNode data = marketdata.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return Optional.empty();
        }
        for (JsonNode row : data) {
            if (!row.isArray()) {
                continue;
            }
            BigDecimal last = decimalAt(row, lastIdx);
            if (last == null) {
                continue;
            }
            BigDecimal prev = decimalAt(row, prevIdx);
            return Optional.of(new RemoteQuote(last, prev, currency));
        }
        return Optional.empty();
    }

    private Optional<RemoteQuote> parseSecurities(JsonNode root) {
        if (root == null) {
            return Optional.empty();
        }
        JsonNode securities = root.path("securities");
        int prevIdx = indexOf(securities.path("columns"), "PREVPRICE");
        JsonNode data = securities.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal prev = decimalAt(data.get(0), prevIdx);
        if (prev == null) {
            return Optional.empty();
        }
        return Optional.of(new RemoteQuote(prev, prev, currency));
    }

    private static int indexOf(JsonNode columns, String name) {
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
        if (idx < 0 || row.size() <= idx || row.get(idx).isNull()) {
            return null;
        }
        return row.get(idx).asText();
    }

    private static BigDecimal decimalAt(JsonNode row, int idx) {
        String text = textAt(row, idx);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
