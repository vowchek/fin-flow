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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Profile("!test")
public class MoexIssClient implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(MoexIssClient.class);

    private final RestClient client;
    private final RestClient yahoo;
    private final String currency;

    public MoexIssClient(
            @Qualifier("moexRestClient") RestClient client,
            @Qualifier("yahooRestClient") RestClient yahoo,
            MarketDataProperties properties
    ) {
        this.client = client;
        this.yahoo = yahoo;
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
    public Optional<InstrumentProfile> fetchInstrumentProfile(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        String secid = externalId.trim().toUpperCase(Locale.ROOT);
        try {
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/iss/securities/{secid}.json")
                            .queryParam("iss.meta", "off")
                            .queryParam("iss.only", "description")
                            .build(secid))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return Optional.empty();
            }
            JsonNode description = root.path("description");
            int nameIdx = indexOf(description.path("columns"), "name");
            int valueIdx = indexOf(description.path("columns"), "value");
            String shortName = null;
            String fullName = null;
            String type = null;
            String group = null;
            String faceUnit = null;
            for (JsonNode row : description.path("data")) {
                if (!row.isArray()) {
                    continue;
                }
                String field = textAt(row, nameIdx);
                String value = textAt(row, valueIdx);
                if (field == null || value == null || value.isBlank()) {
                    continue;
                }
                if ("SHORTNAME".equalsIgnoreCase(field)) {
                    shortName = value;
                } else if ("NAME".equalsIgnoreCase(field) || "SECNAME".equalsIgnoreCase(field)) {
                    fullName = value;
                } else if ("TYPE".equalsIgnoreCase(field)) {
                    type = value;
                } else if ("GROUP".equalsIgnoreCase(field)) {
                    group = value;
                } else if ("FACEUNIT".equalsIgnoreCase(field) || "CURRENCYID".equalsIgnoreCase(field)) {
                    faceUnit = value;
                }
            }
            String name = shortName != null && !shortName.isBlank() ? shortName
                    : (fullName != null && !fullName.isBlank() ? fullName : secid);
            String assetKind = inferAssetKind(type, group);
            String cur = faceUnit != null && !faceUnit.isBlank() ? faceUnit.toUpperCase(Locale.ROOT) : currency;
            if (!"RUB".equals(cur) && !"USD".equals(cur) && !"SUR".equals(cur)) {
                cur = currency;
            }
            if ("SUR".equals(cur)) {
                cur = "RUB";
            }
            return Optional.of(new InstrumentProfile(secid, secid, name, cur, assetKind));
        } catch (RestClientException ex) {
            log.warn("MOEX profile failed for {}: {}", secid, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String inferAssetKind(String type, String group) {
        String hay = ((type == null ? "" : type) + " " + (group == null ? "" : group)).toLowerCase(Locale.ROOT);
        if (hay.contains("bond") || hay.contains("облиг") || hay.contains("exchange_bond")
                || hay.contains("corporate_bond") || hay.contains("ofz") || hay.contains("gov_bond")) {
            return "BOND";
        }
        return "EQUITY";
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
        List<HistoricalPrice> rows = fetchHistoricalRange(externalId, date, date);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().price());
    }

    @Override
    public List<HistoricalPrice> fetchHistoricalRange(String externalId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        String secid = externalId.toUpperCase(Locale.ROOT);
        List<HistoricalPrice> out = new ArrayList<>();
        int start = 0;
        try {
            while (true) {
                int pageStart = start;
                JsonNode root = client.get()
                        .uri(uri -> uri.path("/iss/history/engines/stock/markets/shares/boards/TQBR/securities/{secid}.json")
                                .queryParam("from", from.toString())
                                .queryParam("till", to.toString())
                                .queryParam("start", pageStart)
                                .queryParam("iss.meta", "off")
                                .build(secid))
                        .retrieve()
                        .body(JsonNode.class);
                if (root == null) {
                    break;
                }
                JsonNode history = root.path("history");
                int closeIdx = indexOf(history.path("columns"), "CLOSE");
                int dateIdx = indexOf(history.path("columns"), "TRADEDATE");
                JsonNode data = history.path("data");
                if (!data.isArray() || data.isEmpty() || closeIdx < 0 || dateIdx < 0) {
                    break;
                }
                int pageSize = 0;
                for (JsonNode row : data) {
                    pageSize++;
                    if (!row.isArray()) {
                        continue;
                    }
                    String dateText = textAt(row, dateIdx);
                    BigDecimal close = decimalAt(row, closeIdx);
                    if (dateText == null || close == null) {
                        continue;
                    }
                    try {
                        out.add(new HistoricalPrice(LocalDate.parse(dateText), close));
                    } catch (Exception ignored) {
                        // skip bad row
                    }
                }
                if (pageSize < 100) {
                    break;
                }
                start += pageSize;
            }
        } catch (RestClientException ex) {
            log.warn("MOEX history range failed for {} {}..{}: {}", secid, from, to, ex.getMessage());
        }
        return out;
    }

    private Optional<RemoteQuote> parseMarketdata(JsonNode root) {
        if (root == null) {
            return Optional.empty();
        }
        JsonNode marketdata = root.path("marketdata");
        JsonNode securities = root.path("securities");
        int boardIdx = indexOf(marketdata.path("columns"), "BOARDID");
        int lastIdx = indexOf(marketdata.path("columns"), "LAST");
        int changeIdx = indexOf(marketdata.path("columns"), "CHANGE");
        int changePctIdx = indexOf(marketdata.path("columns"), "LASTTOPREVPRICE");
        if (changePctIdx < 0) {
            changePctIdx = indexOf(marketdata.path("columns"), "LASTCHANGEPRCNT");
        }
        int secBoardIdx = indexOf(securities.path("columns"), "BOARDID");
        int prevIdx = indexOf(securities.path("columns"), "PREVPRICE");
        if (prevIdx < 0) {
            prevIdx = indexOf(securities.path("columns"), "PREVLEGALCLOSEPRICE");
        }
        JsonNode data = marketdata.path("data");
        if (!data.isArray() || data.isEmpty() || lastIdx < 0) {
            return Optional.empty();
        }

        JsonNode preferred = null;
        JsonNode fallback = null;
        for (JsonNode row : data) {
            if (!row.isArray()) {
                continue;
            }
            BigDecimal last = decimalAt(row, lastIdx);
            if (last == null) {
                continue;
            }
            String board = textAt(row, boardIdx);
            if ("TQBR".equalsIgnoreCase(board)) {
                preferred = row;
                break;
            }
            if (fallback == null) {
                fallback = row;
            }
        }
        JsonNode row = preferred != null ? preferred : fallback;
        if (row == null) {
            return Optional.empty();
        }

        BigDecimal last = decimalAt(row, lastIdx);
        if (last == null) {
            return Optional.empty();
        }

        String board = textAt(row, boardIdx);
        BigDecimal prev = previousFromSecurities(securities, secBoardIdx, prevIdx, board);
        if (prev == null) {
            BigDecimal change = decimalAt(row, changeIdx);
            if (change != null) {
                prev = last.subtract(change);
            }
        }
        if (prev == null) {
            BigDecimal changePct = decimalAt(row, changePctIdx);
            if (changePct != null) {
                BigDecimal factor = BigDecimal.ONE.add(changePct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
                if (factor.compareTo(BigDecimal.ZERO) != 0) {
                    prev = last.divide(factor, 8, RoundingMode.HALF_UP);
                }
            }
        }
        if (prev != null && prev.compareTo(BigDecimal.ZERO) <= 0) {
            prev = null;
        }
        return Optional.of(new RemoteQuote(last, prev, currency));
    }

    private static BigDecimal previousFromSecurities(JsonNode securities, int boardIdx, int prevIdx, String board) {
        if (prevIdx < 0 || securities == null || !securities.path("data").isArray()) {
            return null;
        }
        BigDecimal any = null;
        for (JsonNode row : securities.path("data")) {
            if (!row.isArray()) {
                continue;
            }
            BigDecimal prev = decimalAt(row, prevIdx);
            if (prev == null) {
                continue;
            }
            if (board != null && board.equalsIgnoreCase(textAt(row, boardIdx))) {
                return prev;
            }
            if (any == null) {
                any = prev;
            }
        }
        return any;
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

    @Override
    public List<CorporatePayment> fetchCorporatePayments(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return List.of();
        }
        String secid = externalId.trim().toUpperCase(Locale.ROOT);
        List<CorporatePayment> out = new ArrayList<>();
        out.addAll(fetchDividends(secid));
        out.addAll(fetchBondCoupons(secid));
        return out;
    }

    private List<CorporatePayment> fetchDividends(String secid) {
        List<CorporatePayment> fromMoex = fetchDividendsFromMoex(secid);
        if (!fromMoex.isEmpty()) {
            return fromMoex;
        }
        // MOEX /dividends.json currently falls back to security description (no dividends table).
        return fetchDividendsFromYahoo(secid);
    }

    private List<CorporatePayment> fetchDividendsFromMoex(String secid) {
        try {
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/iss/securities/{secid}/dividends.json")
                            .queryParam("iss.meta", "off")
                            .build(secid))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null || !root.has("dividends")) {
                log.debug("MOEX dividends table missing for {}", secid);
                return List.of();
            }
            JsonNode table = root.path("dividends");
            int dateIdx = indexOf(table.path("columns"), "registryclosedate");
            int valueIdx = indexOf(table.path("columns"), "value");
            int currencyIdx = indexOf(table.path("columns"), "currencyid");
            if (dateIdx < 0 || valueIdx < 0 || !table.path("data").isArray()) {
                return List.of();
            }
            List<CorporatePayment> out = new ArrayList<>();
            for (JsonNode row : table.path("data")) {
                if (!row.isArray()) {
                    continue;
                }
                LocalDate date = parseDate(textAt(row, dateIdx));
                BigDecimal value = decimalAt(row, valueIdx);
                if (date == null || value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                String cur = textAt(row, currencyIdx);
                out.add(new CorporatePayment(date, value, cur != null ? cur : currency, "DIVIDEND"));
            }
            return out;
        } catch (RestClientException ex) {
            log.warn("MOEX dividends failed for {}: {}", secid, ex.getMessage());
            return List.of();
        }
    }

    /**
     * Yahoo chart events for {@code SECID.ME} — used while MOEX ISS dividends endpoint is broken.
     */
    private List<CorporatePayment> fetchDividendsFromYahoo(String secid) {
        String yahooSymbol = secid + ".ME";
        long period2 = System.currentTimeMillis() / 1000L;
        long period1 = period2 - (365L * 15L * 24L * 3600L);
        try {
            JsonNode root = yahoo.get()
                    .uri(uri -> uri.path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "1d")
                            .queryParam("events", "div")
                            .queryParam("period1", period1)
                            .queryParam("period2", period2)
                            .build(yahooSymbol))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return List.of();
            }
            JsonNode dividends = root.path("chart").path("result");
            if (!dividends.isArray() || dividends.isEmpty()) {
                return List.of();
            }
            JsonNode events = dividends.get(0).path("events").path("dividends");
            if (!events.isObject() || events.isEmpty()) {
                log.debug("Yahoo dividends empty for {}", yahooSymbol);
                return List.of();
            }
            List<CorporatePayment> out = new ArrayList<>();
            var fields = events.fields();
            while (fields.hasNext()) {
                JsonNode node = fields.next().getValue();
                BigDecimal amount = decimalNode(node.get("amount"));
                long epoch = node.path("date").asLong(0);
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || epoch <= 0) {
                    continue;
                }
                LocalDate date = LocalDate.ofInstant(
                        java.time.Instant.ofEpochSecond(epoch),
                        java.time.ZoneOffset.UTC
                );
                out.add(new CorporatePayment(date, amount, currency, "DIVIDEND"));
            }
            log.info("Yahoo dividends for {}: {} rows", yahooSymbol, out.size());
            return out;
        } catch (RestClientException ex) {
            log.warn("Yahoo dividends failed for {}: {}", yahooSymbol, ex.getMessage());
            return List.of();
        }
    }

    private static BigDecimal decimalNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<CorporatePayment> fetchBondCoupons(String secid) {
        try {
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/iss/statistics/engines/stock/markets/bonds/bondization/{secid}.json")
                            .queryParam("iss.meta", "off")
                            .queryParam("iss.only", "coupons")
                            .build(secid))
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return List.of();
            }
            JsonNode table = root.path("coupons");
            int dateIdx = indexOf(table.path("columns"), "coupondate");
            if (dateIdx < 0) {
                dateIdx = indexOf(table.path("columns"), "date");
            }
            int valueIdx = indexOf(table.path("columns"), "value");
            int currencyIdx = indexOf(table.path("columns"), "faceunit");
            List<CorporatePayment> out = new ArrayList<>();
            for (JsonNode row : table.path("data")) {
                if (!row.isArray()) {
                    continue;
                }
                LocalDate date = parseDate(textAt(row, dateIdx));
                BigDecimal value = decimalAt(row, valueIdx);
                if (date == null || value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                String cur = textAt(row, currencyIdx);
                out.add(new CorporatePayment(date, value, cur != null ? cur : currency, "COUPON"));
            }
            return out;
        } catch (RestClientException ex) {
            log.debug("MOEX bond coupons unavailable for {}: {}", secid, ex.getMessage());
            return List.of();
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, Math.min(10, raw.length())));
        } catch (Exception ex) {
            return null;
        }
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
