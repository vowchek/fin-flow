package com.ledger.api.dto;

import java.math.BigDecimal;

public record PortfolioSettingsResponse(
        String name,
        BigDecimal taxRatePercent,
        BigDecimal investedAmount
) {
}
