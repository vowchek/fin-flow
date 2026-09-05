package com.ledger.api.dto;

import java.math.BigDecimal;

public record PassiveIncomeResponse(
        int year,
        BigDecimal taxRatePercent,
        BigDecimal annualGross,
        BigDecimal annualNet,
        BigDecimal monthlyNet,
        String currency,
        /** ACTUAL — из таблицы выплат; FORECAST — прогноз; NONE — нет данных. */
        String basis,
        /** Медианный YoY рост, % (для прогноза); иначе null. */
        BigDecimal growthPct
) {
}
