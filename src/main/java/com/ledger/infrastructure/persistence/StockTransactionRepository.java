package com.ledger.infrastructure.persistence;

import com.ledger.domain.StockTransaction;
import com.ledger.domain.TradeSide;
import com.ledger.domain.TxKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {

    List<StockTransaction> findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(UUID holdingId);

    Optional<StockTransaction> findFirstByHoldingIdAndSideOrderByOccurredOnAscCreatedAtAsc(UUID holdingId, TradeSide side);

    Optional<StockTransaction> findFirstByHoldingIdAndSideAndKindOrderByOccurredOnAscCreatedAtAsc(
            UUID holdingId,
            TradeSide side,
            TxKind kind
    );

    List<StockTransaction> findByRelatedHoldingIdAndKindIn(UUID relatedHoldingId, Collection<TxKind> kinds);
}
