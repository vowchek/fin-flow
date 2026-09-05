package com.ledger.api.dto;

import com.ledger.domain.AssetKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InstrumentUpsertRequest(
        @NotBlank @Size(max = 32) String symbol,
        @NotBlank @Size(max = 120) String externalId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 8) String currency,
        Boolean enabled,
        AssetKind assetKind,
        @DecimalMin(value = "0", inclusive = true) @Digits(integer = 20, fraction = 8) BigDecimal annualCashflowPerUnit,
        @Digits(integer = 6, fraction = 6) BigDecimal cashflowGrowthPct,
        @Min(1990) @Max(2200) Integer cashflowUntilYear
) {
}
