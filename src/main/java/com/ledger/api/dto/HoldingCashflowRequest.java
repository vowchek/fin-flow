package com.ledger.api.dto;

import com.ledger.domain.AssetKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record HoldingCashflowRequest(
        AssetKind assetKind,
        @DecimalMin(value = "0", inclusive = true) @Digits(integer = 20, fraction = 8) BigDecimal annualCashflowPerUnit,
        @Digits(integer = 6, fraction = 6) BigDecimal cashflowGrowthPct,
        @Min(1990) @Max(2200) Integer cashflowUntilYear
) {
}
