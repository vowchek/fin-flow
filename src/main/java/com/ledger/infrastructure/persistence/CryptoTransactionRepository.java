package com.ledger.infrastructure.persistence;

import com.ledger.domain.CryptoTransaction;
import com.ledger.domain.TradeSide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CryptoTransactionRepository extends JpaRepository<CryptoTransaction, UUID> {

    List<CryptoTransaction> findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(UUID holdingId);

    Optional<CryptoTransaction> findFirstByHoldingIdAndSideOrderByOccurredOnAscCreatedAtAsc(UUID holdingId, TradeSide side);
}
