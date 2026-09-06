package com.ledger.infrastructure.persistence;

import com.ledger.domain.StockInstrumentPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockInstrumentPaymentRepository extends JpaRepository<StockInstrumentPayment, UUID> {

    List<StockInstrumentPayment> findByInstrumentIdOrderByOccurredOnDesc(UUID instrumentId);

    Page<StockInstrumentPayment> findByInstrumentIdOrderByOccurredOnDesc(UUID instrumentId, Pageable pageable);

    @Query("""
            select p from StockInstrumentPayment p
            join fetch p.instrument i
            where i.id in :ids
            order by p.occurredOn desc
            """)
    List<StockInstrumentPayment> findByInstrumentIdIn(@Param("ids") List<UUID> ids);

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("delete from StockInstrumentPayment p where p.instrument.id = :instrumentId")
    void deleteByInstrumentId(@Param("instrumentId") UUID instrumentId);
}