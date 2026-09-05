package com.ledger.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CashMovementRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        LocalDate occurredOn,
        @Size(max = 1000) String note,
        UUID relatedHoldingId,
        /**
         * For DIVIDEND/COUPON: when {@code false}, income is attributed to the asset
         * but cash balance is not increased (spent / reinvested outside). Default {@code true}.
         */
        Boolean settleToCash
) {
}
