package com.ledger.infrastructure.persistence;

import com.ledger.domain.PriceHistory;
import com.ledger.domain.AssetMarket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, UUID> {

    Optional<PriceHistory> findByMarketAndInstrumentIdAndPriceDate(
            AssetMarket market,
            UUID instrumentId,
            LocalDate priceDate
    );
}
