package com.ledger.infrastructure.persistence;

import com.ledger.domain.CryptoTransaction;
import com.ledger.domain.TradeSide;
import com.ledger.domain.TxKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CryptoTransactionRepository extends JpaRepository<CryptoTransaction, UUID> {

    List<CryptoTransaction> findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(UUID holdingId);

    Page<CryptoTransaction> findByHoldingIdOrderByOccurredOnDescCreatedAtDesc(UUID holdingId, Pageable pageable);

    Optional<CryptoTransaction> findFirstByHoldingIdAndSideOrderByOccurredOnAscCreatedAtAsc(UUID holdingId, TradeSide side);

    List<CryptoTransaction> findByRelatedHoldingIdAndKindIn(UUID relatedHoldingId, Collection<TxKind> kinds);
}
