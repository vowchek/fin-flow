package com.ledger.infrastructure.persistence;

import com.ledger.domain.ExpenseCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, UUID> {

    List<ExpenseCategoryEntity> findByOwnerIdOrderByDisplayOrderAsc(UUID ownerId);

    List<ExpenseCategoryEntity> findByOwnerIdAndIdIn(UUID ownerId, List<UUID> ids);

    int countByOwnerId(UUID ownerId);
}
