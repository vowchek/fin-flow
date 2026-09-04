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
        String logoUrl,
        BigDecimal unitPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal dayChangeAbs,
        BigDecimal dayChangePct,
        BigDecimal totalChangeAbs,
        BigDecimal totalChangePct,
        String currency,
        Instant priceAsOf,
        Instant createdAt,
        Instant updatedAt
) {
}
