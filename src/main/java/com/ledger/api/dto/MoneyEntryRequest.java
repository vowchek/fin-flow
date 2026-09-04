package com.ledger.api.dto;

import com.ledger.domain.MoneyDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MoneyEntryRequest(
        UUID projectId,
        @NotNull MoneyDirection direction,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull LocalDate occurredOn,
        @Size(max = 80) String category,
        @Size(max = 1000) String note
) {
}
