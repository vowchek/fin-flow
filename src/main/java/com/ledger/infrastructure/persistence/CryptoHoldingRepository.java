package com.ledger.infrastructure.persistence;

import com.ledger.domain.CryptoHolding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CryptoHoldingRepository extends JpaRepository<CryptoHolding, UUID> {

    boolean existsByPortfolioIdAndSymbol(UUID portfolioId, String symbol);

    Optional<CryptoHolding> findByIdAndPortfolioId(UUID id, UUID portfolioId);
}
