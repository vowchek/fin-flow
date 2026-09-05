package com.ledger.api.dto;

import com.ledger.domain.AssetKind;

import java.math.BigDecimal;
import java.util.UUID;

public record InstrumentResponse(
        UUID id,
        String market,
        String symbol,
        String externalId,
        String name,
        String logoUrl,
        String currency,
        boolean enabled,
        AssetKind assetKind,
        BigDecimal annualCashflowPerUnit,
        BigDecimal cashflowGrowthPct,
        Integer cashflowUntilYear,
        Boolean paysDividends
) {
}
