package com.ledger.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        String currency,
        String yearMonth,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
