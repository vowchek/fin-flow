package com.ledger.infrastructure.persistence;

import com.ledger.domain.PriceQuote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceQuoteRepository extends JpaRepository<PriceQuote, PriceQuote.QuoteId> {
}
