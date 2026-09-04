package com.ledger.infrastructure.persistence;

import com.ledger.domain.StockHolding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockHoldingRepository extends JpaRepository<StockHolding, UUID> {

    boolean existsByPortfolioIdAndSymbol(UUID portfolioId, String symbol);

    Optional<StockHolding> findByIdAndPortfolioId(UUID id, UUID portfolioId);
}
