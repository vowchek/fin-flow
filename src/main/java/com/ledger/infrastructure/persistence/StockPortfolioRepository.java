package com.ledger.infrastructure.persistence;

import com.ledger.domain.StockPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockPortfolioRepository extends JpaRepository<StockPortfolio, UUID> {

    List<StockPortfolio> findAllByOwnerIdOrderByNameAsc(UUID ownerId);

    Optional<StockPortfolio> findByIdAndOwnerId(UUID id, UUID ownerId);
}
