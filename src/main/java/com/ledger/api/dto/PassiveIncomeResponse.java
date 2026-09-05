package com.ledger.api.dto;

import java.math.BigDecimal;

public record PassiveIncomeResponse(
        int year,
        BigDecimal taxRatePercent,
        BigDecimal annualGross,
        BigDecimal annualNet,
        BigDecimal monthlyNet,
        String currency
) {
}
