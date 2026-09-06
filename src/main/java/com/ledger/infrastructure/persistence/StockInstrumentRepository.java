package com.ledger.infrastructure.persistence;

import com.ledger.domain.StockInstrument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockInstrumentRepository extends JpaRepository<StockInstrument, UUID> {

    Optional<StockInstrument> findBySymbol(String symbol);

    Optional<StockInstrument> findByExternalId(String externalId);

    List<StockInstrument> findAllByEnabledTrueOrderBySymbolAsc();

    List<StockInstrument> findAllByOrderBySymbolAsc();

    @Query("""
            select i from StockInstrument i
            where i.enabled = true
              and (:q = '' or upper(i.symbol) like upper(concat('%', :q, '%'))
                   or upper(i.name) like upper(concat('%', :q, '%')))
            order by i.symbol
            """)
    List<StockInstrument> searchEnabled(@Param("q") String q);

    @Query("""
            select i from StockInstrument i
            where i.enabled = true
              and (:q = '' or upper(i.symbol) like upper(concat('%', :q, '%'))
                   or upper(i.name) like upper(concat('%', :q, '%'))
                   or upper(i.externalId) like upper(concat('%', :q, '%')))
            """)
    Page<StockInstrument> searchEnabledPaged(@Param("q") String q, Pageable pageable);

    @Query("""
            select i from StockInstrument i
            where (:q = '' or upper(i.symbol) like upper(concat('%', :q, '%'))
                   or upper(i.name) like upper(concat('%', :q, '%'))
                   or upper(i.externalId) like upper(concat('%', :q, '%')))
            """)
    Page<StockInstrument> searchAllPaged(@Param("q") String q, Pageable pageable);

    boolean existsBySymbolIgnoreCase(String symbol);

    boolean existsByExternalIdIgnoreCase(String externalId);
}
