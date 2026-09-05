package com.ledger.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        LocalDate occurredOn,
        @DecimalMin(value = "0", inclusive = false) BigDecimal unitPrice,
        @Size(max = 1000) String note,
        /** When true (default), purchase spend is added to portfolio investedAmount. */
        Boolean addToInvested
) {
}
