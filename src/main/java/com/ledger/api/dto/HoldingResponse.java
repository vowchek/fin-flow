package com.ledger.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HoldingResponse(
        UUID id,
        String symbol,
        String name,
        BigDecimal quantity,
        LocalDate openedOn,
        boolean cash,
        String logoUrl,
        BigDecimal unitPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal dayChangeAbs,
        BigDecimal dayChangePct,
        BigDecimal totalChangeAbs,
        BigDecimal totalChangePct,
        BigDecimal incomeAbs,
        /** Part of incomeAbs that did not credit cash (settleToCash=false). Informational only. */
        BigDecimal reinvestedIncomeAbs,
        String currency,
        Instant priceAsOf,
        Instant createdAt,
        Instant updatedAt,
        /** Expected dividend/coupon for the current dividend-year (portfolio qty × per unit). */
        BigDecimal expectedIncomeAbs,
        /** ACTUAL | FORECAST | NONE */
        String expectedIncomeBasis
) {
}
