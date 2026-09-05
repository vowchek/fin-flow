package com.ledger.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record HoldingCreateRequest(
        UUID instrumentId,
        @Size(max = 32) String symbol,
        @Size(max = 120) String name,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        LocalDate occurredOn,
        @DecimalMin(value = "0", inclusive = false) BigDecimal unitPrice,
        @Size(max = 1000) String note,
        /** When true (default), purchase spend is added to portfolio investedAmount. */
        Boolean addToInvested
) {
}
