package com.ledger.infrastructure.persistence;

import com.ledger.domain.StockTransaction;
import com.ledger.domain.TradeSide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {

    List<StockTransaction> findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(UUID holdingId);

    Optional<StockTransaction> findFirstByHoldingIdAndSideOrderByOccurredOnAscCreatedAtAsc(UUID holdingId, TradeSide side);
}
