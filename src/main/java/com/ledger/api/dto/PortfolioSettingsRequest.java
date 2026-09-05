package com.ledger.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PortfolioSettingsRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull
        @DecimalMin(value = "0", inclusive = true)
        @DecimalMax(value = "100", inclusive = true)
        BigDecimal taxRatePercent,
        @NotNull
        @DecimalMin(value = "0", inclusive = true)
        BigDecimal investedAmount
) {
}
