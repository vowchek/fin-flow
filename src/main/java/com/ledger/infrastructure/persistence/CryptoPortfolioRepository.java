package com.ledger.infrastructure.persistence;

import com.ledger.domain.CryptoPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CryptoPortfolioRepository extends JpaRepository<CryptoPortfolio, UUID> {

    List<CryptoPortfolio> findAllByOwnerIdOrderByNameAsc(UUID ownerId);

    Optional<CryptoPortfolio> findByIdAndOwnerId(UUID id, UUID ownerId);
}
