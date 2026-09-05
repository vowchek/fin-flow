package com.ledger.api.dto;

import com.ledger.domain.AssetKind;

import java.math.BigDecimal;
import java.util.UUID;

public record HoldingCashflowResponse(
        UUID holdingId,
        String symbol,
        AssetKind assetKind,
        BigDecimal annualCashflowPerUnit,
        BigDecimal cashflowGrowthPct,
        Integer cashflowUntilYear
) {
}
