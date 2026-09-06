package com.ledger.infrastructure.persistence;

import com.ledger.domain.ExpenseEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseEntryRepository extends JpaRepository<ExpenseEntry, UUID> {

    List<ExpenseEntry> findByOwnerIdAndOccurredMonthGreaterThanEqualAndOccurredMonthLessThanEqualOrderByOccurredMonthDescCreatedAtDesc(
            UUID ownerId,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );

    Optional<ExpenseEntry> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("""
            SELECT e.category.id, e.category.name, FUNCTION('to_char', e.occurredMonth, 'YYYY-MM'), SUM(e.amount)
            FROM ExpenseEntry e
            WHERE e.owner.id = :ownerId
              AND e.occurredMonth >= :fromDate
              AND e.occurredMonth <= :toDate
            GROUP BY e.category.id, e.category.name, FUNCTION('to_char', e.occurredMonth, 'YYYY-MM')
            ORDER BY e.category.name, FUNCTION('to_char', e.occurredMonth, 'YYYY-MM')
            """)
    List<Object[]> sumByCategoryAndMonth(
            @Param("ownerId") UUID ownerId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    long deleteByOwnerIdAndCategoryIdAndOccurredMonthGreaterThanEqualAndOccurredMonthLessThanEqual(
            UUID ownerId,
            UUID categoryId,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );
}
