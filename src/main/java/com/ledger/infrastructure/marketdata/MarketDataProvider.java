package com.ledger.infrastructure.marketdata;

import com.ledger.domain.AssetMarket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MarketDataProvider {

    AssetMarket market();

    List<RemoteInstrument> search(String query);

    /**
     * Richer metadata for a known external id (SECID / coin id). Default: empty.
     */
    default Optional<InstrumentProfile> fetchInstrumentProfile(String externalId) {
        return Optional.empty();
    }

    Optional<RemoteQuote> fetchLive(String externalId);

    /**
     * Live quotes keyed by external id. Default walks one-by-one; providers should batch when possible.
     */
    default Map<String, RemoteQuote> fetchLiveMany(Collection<String> externalIds) {
        Map<String, RemoteQuote> out = new LinkedHashMap<>();
        if (externalIds == null) {
            return out;
        }
        for (String id : externalIds) {
            if (id == null || id.isBlank() || out.containsKey(id)) {
                continue;
            }
            fetchLive(id).ifPresent(quote -> out.put(id, quote));
        }
        return out;
    }

    Optional<BigDecimal> fetchHistorical(String externalId, LocalDate date);

    /**
     * Corporate cash payments (dividends / coupons) for a security. Default: empty.
     */
    default List<CorporatePayment> fetchCorporatePayments(String externalId) {
        return List.of();
    }

    /**
     * EOD closes in [from, to]. Default walks day-by-day; providers should override with a range API.
     */
    default List<HistoricalPrice> fetchHistoricalRange(String externalId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        List<HistoricalPrice> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDate day = d;
            fetchHistorical(externalId, day).ifPresent(price -> out.add(new HistoricalPrice(day, price)));
        }
        return out;
    }

    record RemoteInstrument(
            String symbol,
            String externalId,
            String name,
            String logoUrl,
            String currency
    ) {
    }

    record RemoteQuote(
            BigDecimal price,
            BigDecimal previousClose,
            String currency
    ) {
    }

    record HistoricalPrice(
            LocalDate date,
            BigDecimal price
    ) {
    }

    record CorporatePayment(
            LocalDate date,
            BigDecimal valuePerUnit,
            String currency,
            String kind
    ) {
    }

    record InstrumentProfile(
            String symbol,
            String externalId,
            String name,
            String currency,
            /** {@code EQUITY} or {@code BOND}; null if unknown. */
            String assetKind
    ) {
    }
}
