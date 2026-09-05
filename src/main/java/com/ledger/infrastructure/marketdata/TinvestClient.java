package com.ledger.infrastructure.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@Profile("!test")
public class TinvestClient {

    private static final Logger log = LoggerFactory.getLogger(TinvestClient.class);
    private static final String FIND =
            "/tinkoff.public.invest.api.contract.v1.InstrumentsService/FindInstrument";
    private static final String DIVIDENDS =
            "/tinkoff.public.invest.api.contract.v1.InstrumentsService/GetDividends";
    private static final String COUPONS =
            "/tinkoff.public.invest.api.contract.v1.InstrumentsService/GetBondCoupons";
    private static final String SHARE_BY =
            "/tinkoff.public.invest.api.contract.v1.InstrumentsService/ShareBy";
    private static final String BOND_BY =
            "/tinkoff.public.invest.api.contract.v1.InstrumentsService/BondBy";

    private final RestClient client;
    private final MarketDataProperties.Tinvest props;

    public TinvestClient(
            @Qualifier("tinvestRestClient") RestClient client,
            MarketDataProperties properties
    ) {
        this.client = client;
        this.props = properties.getTinvest();
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public List<RemoteHit> search(String query) {
        requireConfigured();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        try {
            JsonNode root = post(FIND, Map.of(
                    "query", q,
                    "instrumentKind", "INSTRUMENT_TYPE_UNSPECIFIED"
            ));
            List<RemoteHit> out = new ArrayList<>();
            for (JsonNode n : root.path("instruments")) {
                RemoteHit hit = parseHit(n);
                if (hit != null) {
                    out.add(hit);
                }
            }
            out.sort(Comparator
                    .comparing((RemoteHit h) -> score(h, q)).reversed()
                    .thenComparing(RemoteHit::ticker));
            return out.size() > 40 ? out.subList(0, 40) : out;
        } catch (RestClientException ex) {
            log.warn("T-Invest FindInstrument failed for '{}': {}", q, ex.getMessage());
            return List.of();
        }
    }

    public Optional<RemoteHit> resolve(String tickerOrId) {
        requireConfigured();
        String want = tickerOrId == null ? "" : tickerOrId.trim();
        if (want.isEmpty()) {
            return Optional.empty();
        }
        List<RemoteHit> hits = search(want);
        String upper = want.toUpperCase(Locale.ROOT);
        return hits.stream()
                .filter(h -> upper.equalsIgnoreCase(h.ticker()) || upper.equalsIgnoreCase(h.uid())
                        || upper.equalsIgnoreCase(h.figi()) || upper.equalsIgnoreCase(h.isin()))
                .max(Comparator.comparingInt(h -> preferClass(h.classCode())));
    }

    public Optional<InstrumentDetails> fetchDetails(RemoteHit hit) {
        requireConfigured();
        if (hit == null || hit.uid() == null) {
            return Optional.empty();
        }
        try {
            if ("share".equalsIgnoreCase(hit.instrumentType())
                    || "INSTRUMENT_TYPE_SHARE".equalsIgnoreCase(hit.instrumentType())) {
                JsonNode root = post(SHARE_BY, Map.of(
                        "idType", "INSTRUMENT_ID_TYPE_UID",
                        "id", hit.uid()
                ));
                return Optional.ofNullable(parseShare(root.path("instrument"), hit));
            }
            if ("bond".equalsIgnoreCase(hit.instrumentType())
                    || "INSTRUMENT_TYPE_BOND".equalsIgnoreCase(hit.instrumentType())) {
                JsonNode root = post(BOND_BY, Map.of(
                        "idType", "INSTRUMENT_ID_TYPE_UID",
                        "id", hit.uid()
                ));
                return Optional.ofNullable(parseBond(root.path("instrument"), hit));
            }
            return Optional.of(new InstrumentDetails(
                    hit.ticker(),
                    hit.ticker(),
                    hit.name(),
                    "RUB",
                    hit.assetKind(),
                    hit.uid(),
                    hit.figi(),
                    hit.isin(),
                    hit.classCode(),
                    null
            ));
        } catch (RestClientException ex) {
            log.warn("T-Invest details failed for {}: {}", hit.ticker(), ex.getMessage());
            return Optional.empty();
        }
    }

    public List<MarketDataProvider.CorporatePayment> fetchPayments(RemoteHit hit) {
        requireConfigured();
        if (hit == null || hit.uid() == null || hit.uid().isBlank()) {
            return List.of();
        }
        List<MarketDataProvider.CorporatePayment> out = new ArrayList<>();
        if ("BOND".equals(hit.assetKind())) {
            out.addAll(fetchCoupons(hit.uid()));
        } else {
            out.addAll(fetchDividends(hit.uid()));
            // some instruments might be bonds mis-typed; coupons are harmless if empty
            if (out.isEmpty()) {
                out.addAll(fetchCoupons(hit.uid()));
            }
        }
        return out;
    }

    private List<MarketDataProvider.CorporatePayment> fetchDividends(String instrumentId) {
        try {
            Instant from = Instant.now().minusSeconds(365L * 15 * 24 * 3600);
            Instant to = Instant.now().plusSeconds(365L * 24 * 3600);
            JsonNode root = post(DIVIDENDS, Map.of(
                    "instrumentId", instrumentId,
                    "from", from.toString(),
                    "to", to.toString()
            ));
            List<MarketDataProvider.CorporatePayment> out = new ArrayList<>();
            for (JsonNode n : root.path("dividends")) {
                String type = text(n, "dividendType");
                if (type != null && type.toLowerCase(Locale.ROOT).contains("cancel")) {
                    continue;
                }
                LocalDate date = timestampDate(n.get("recordDate"));
                if (date == null) {
                    date = timestampDate(n.get("paymentDate"));
                }
                Money amount = money(n.get("dividendNet"));
                if (date == null || amount == null || amount.value().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                out.add(new MarketDataProvider.CorporatePayment(
                        date, amount.value(), amount.currency(), "DIVIDEND"));
            }
            log.info("T-Invest dividends for {}: {} rows", instrumentId, out.size());
            return out;
        } catch (RestClientException ex) {
            log.warn("T-Invest GetDividends failed for {}: {}", instrumentId, ex.getMessage());
            return List.of();
        }
    }

    private List<MarketDataProvider.CorporatePayment> fetchCoupons(String instrumentId) {
        try {
            Instant from = Instant.now().minusSeconds(365L * 5 * 24 * 3600);
            Instant to = Instant.now().plusSeconds(365L * 10 * 24 * 3600);
            JsonNode root = post(COUPONS, Map.of(
                    "instrumentId", instrumentId,
                    "from", from.toString(),
                    "to", to.toString()
            ));
            List<MarketDataProvider.CorporatePayment> out = new ArrayList<>();
            for (JsonNode n : root.path("events")) {
                LocalDate date = timestampDate(n.get("couponDate"));
                if (date == null) {
                    date = timestampDate(n.get("payDate"));
                }
                Money amount = money(n.get("payOneBond"));
                if (amount == null) {
                    amount = money(n.get("coupon"));
                }
                if (date == null || amount == null || amount.value().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                // only past and near coupons for history; include all positive pays
                out.add(new MarketDataProvider.CorporatePayment(
                        date, amount.value(), amount.currency(), "COUPON"));
            }
            log.info("T-Invest coupons for {}: {} rows", instrumentId, out.size());
            return out;
        } catch (RestClientException ex) {
            log.warn("T-Invest GetBondCoupons failed for {}: {}", instrumentId, ex.getMessage());
            return List.of();
        }
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("TINVEST_TOKEN is not configured");
        }
    }

    private static RemoteHit parseHit(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return null;
        }
        String ticker = text(n, "ticker");
        String uid = text(n, "uid");
        if (ticker == null || ticker.isBlank() || uid == null || uid.isBlank()) {
            return null;
        }
        String type = text(n, "instrumentType");
        String classCode = text(n, "classCode");
        String name = text(n, "name");
        if (name == null || name.isBlank()) {
            name = ticker;
        }
        return new RemoteHit(
                ticker.toUpperCase(Locale.ROOT),
                uid,
                text(n, "figi"),
                text(n, "isin"),
                name,
                classCode,
                type,
                inferAssetKind(type, classCode)
        );
    }

    private static InstrumentDetails parseShare(JsonNode n, RemoteHit hit) {
        if (n == null || n.isMissingNode() || n.isEmpty()) {
            return detailsFromHit(hit);
        }
        String ticker = text(n, "ticker");
        if (ticker == null) {
            ticker = hit.ticker();
        }
        String name = text(n, "name");
        if (name == null || name.isBlank()) {
            name = hit.name();
        }
        String currency = text(n, "currency");
        String logo = null;
        JsonNode brand = n.path("brand");
        if (brand.isObject()) {
            logo = text(brand, "logoName");
        }
        return new InstrumentDetails(
                ticker.toUpperCase(Locale.ROOT),
                ticker.toUpperCase(Locale.ROOT),
                name,
                normalizeCurrency(currency),
                "EQUITY",
                text(n, "uid") != null ? text(n, "uid") : hit.uid(),
                text(n, "figi") != null ? text(n, "figi") : hit.figi(),
                text(n, "isin") != null ? text(n, "isin") : hit.isin(),
                text(n, "classCode") != null ? text(n, "classCode") : hit.classCode(),
                logo
        );
    }

    private static InstrumentDetails parseBond(JsonNode n, RemoteHit hit) {
        if (n == null || n.isMissingNode() || n.isEmpty()) {
            return detailsFromHit(hit);
        }
        String ticker = text(n, "ticker");
        if (ticker == null) {
            ticker = hit.ticker();
        }
        String name = text(n, "name");
        if (name == null || name.isBlank()) {
            name = hit.name();
        }
        String currency = text(n, "currency");
        if (currency == null) {
            currency = text(n, "nominal") != null ? null : text(n.path("nominal"), "currency");
        }
        return new InstrumentDetails(
                ticker.toUpperCase(Locale.ROOT),
                ticker.toUpperCase(Locale.ROOT),
                name,
                normalizeCurrency(currency),
                "BOND",
                text(n, "uid") != null ? text(n, "uid") : hit.uid(),
                text(n, "figi") != null ? text(n, "figi") : hit.figi(),
                text(n, "isin") != null ? text(n, "isin") : hit.isin(),
                text(n, "classCode") != null ? text(n, "classCode") : hit.classCode(),
                null
        );
    }

    private static InstrumentDetails detailsFromHit(RemoteHit hit) {
        return new InstrumentDetails(
                hit.ticker(),
                hit.ticker(),
                hit.name(),
                "RUB",
                hit.assetKind(),
                hit.uid(),
                hit.figi(),
                hit.isin(),
                hit.classCode(),
                null
        );
    }

    private static String inferAssetKind(String type, String classCode) {
        String t = (type == null ? "" : type).toLowerCase(Locale.ROOT);
        String c = (classCode == null ? "" : classCode).toUpperCase(Locale.ROOT);
        if (t.contains("bond") || c.startsWith("TQCB") || c.startsWith("TQOB") || c.startsWith("TQIR")) {
            return "BOND";
        }
        return "EQUITY";
    }

    private static int score(RemoteHit h, String q) {
        String upper = q.toUpperCase(Locale.ROOT);
        int s = preferClass(h.classCode());
        if (upper.equalsIgnoreCase(h.ticker())) {
            s += 1000;
        } else if (h.ticker().toUpperCase(Locale.ROOT).startsWith(upper)) {
            s += 100;
        }
        return s;
    }

    private static int preferClass(String classCode) {
        if (classCode == null) {
            return 0;
        }
        return switch (classCode.toUpperCase(Locale.ROOT)) {
            case "TQBR" -> 50;
            case "TQTF" -> 40;
            case "TQCB", "TQOB" -> 45;
            default -> 10;
        };
    }

    private static String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return "RUB";
        }
        String c = raw.trim().toUpperCase(Locale.ROOT);
        if ("RUB".equals(c) || "SUR".equals(c)) {
            return "RUB";
        }
        if ("USD".equals(c)) {
            return "USD";
        }
        return "RUB";
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        String v = n.get(field).asText(null);
        return v == null || v.isBlank() ? null : v;
    }

    private static LocalDate timestampDate(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String raw = node.asText();
            if (raw.length() >= 10) {
                try {
                    return LocalDate.parse(raw.substring(0, 10));
                } catch (Exception ignored) {
                    // fall through
                }
            }
            try {
                return Instant.parse(raw).atZone(ZoneOffset.UTC).toLocalDate();
            } catch (Exception ex) {
                return null;
            }
        }
        if (node.has("seconds")) {
            long seconds = node.path("seconds").asLong(0);
            if (seconds > 0) {
                return Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC).toLocalDate();
            }
        }
        return null;
    }

    private static Money money(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String currency = text(node, "currency");
        long units = longValue(node.get("units"));
        int nano = node.path("nano").asInt(0);
        BigDecimal value = BigDecimal.valueOf(units)
                .add(BigDecimal.valueOf(nano, 9))
                .setScale(8, RoundingMode.HALF_UP);
        return new Money(value, normalizeCurrency(currency));
    }

    private static long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0L;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        try {
            return Long.parseLong(node.asText("0").trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public record RemoteHit(
            String ticker,
            String uid,
            String figi,
            String isin,
            String name,
            String classCode,
            String instrumentType,
            String assetKind
    ) {
    }

    public record InstrumentDetails(
            String symbol,
            String externalId,
            String name,
            String currency,
            String assetKind,
            String uid,
            String figi,
            String isin,
            String classCode,
            String logoName
    ) {
    }

    private record Money(BigDecimal value, String currency) {
    }
}
