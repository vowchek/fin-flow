package com.ledger.infrastructure.persistence;

import com.ledger.domain.CryptoInstrument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CryptoInstrumentRepository extends JpaRepository<CryptoInstrument, UUID> {

    Optional<CryptoInstrument> findBySymbol(String symbol);

    Optional<CryptoInstrument> findByExternalId(String externalId);

    List<CryptoInstrument> findAllByEnabledTrueOrderBySymbolAsc();

    List<CryptoInstrument> findAllByOrderBySymbolAsc();

    @Query("""
            select i from CryptoInstrument i
            where i.enabled = true
              and (:q = '' or upper(i.symbol) like upper(concat('%', :q, '%'))
                   or upper(i.name) like upper(concat('%', :q, '%')))
            order by i.symbol
            """)
    List<CryptoInstrument> searchEnabled(@Param("q") String q);

    @Query("""
            select i from CryptoInstrument i
            where i.enabled = true
              and (:q = '' or upper(i.symbol) like upper(concat('%', :q, '%'))
                   or upper(i.name) like upper(concat('%', :q, '%'))
                   or upper(i.externalId) like upper(concat('%', :q, '%')))
            """)
    Page<CryptoInstrument> searchEnabledPaged(@Param("q") String q, Pageable pageable);

    @Query("""
            select i from CryptoInstrument i
            where (:q = '' or upper(i.symbol) like upper(concat('%', :q, '%'))
                   or upper(i.name) like upper(concat('%', :q, '%'))
                   or upper(i.externalId) like upper(concat('%', :q, '%')))
            """)
    Page<CryptoInstrument> searchAllPaged(@Param("q") String q, Pageable pageable);

    boolean existsBySymbolIgnoreCase(String symbol);

    boolean existsByExternalIdIgnoreCase(String externalId);
}
