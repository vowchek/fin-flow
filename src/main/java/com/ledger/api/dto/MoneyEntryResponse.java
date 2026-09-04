package com.ledger.api.dto;

import com.ledger.domain.MoneyDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MoneyEntryResponse(
        UUID id,
        UUID projectId,
        MoneyDirection direction,
        BigDecimal amount,
        String currency,
        LocalDate occurredOn,
        String category,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
