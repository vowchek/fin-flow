package com.ledger.infrastructure.marketdata;

import com.ledger.domain.AssetMarket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketDataProvider {

    AssetMarket market();

    List<RemoteInstrument> search(String query);

    Optional<RemoteQuote> fetchLive(String externalId);

    Optional<BigDecimal> fetchHistorical(String externalId, LocalDate date);

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
}
