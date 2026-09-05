package com.ledger.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record HoldingDetailResponse(
        UUID holdingId,
        String symbol,
        String name,
        String logoUrl,
        boolean cash,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal dayChangeAbs,
        BigDecimal dayChangePct,
        BigDecimal marketValue,
        String currency,
        List<ValuePointResponse> priceHistory,
        /** Dividend/coupon per 1 share for current year; null if N/A. */
        BigDecimal dividendThisYearPerUnit,
        String dividendThisYearBasis,
        BigDecimal dividendNextYearPerUnit,
        String dividendNextYearBasis
) {
}
