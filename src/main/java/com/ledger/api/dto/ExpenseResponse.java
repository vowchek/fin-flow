package com.ledger.api.dto;

import com.ledger.domain.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        ExpenseCategory category,
        BigDecimal amount,
        String currency,
        String yearMonth,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
