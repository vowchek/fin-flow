package com.ledger.api.dto;

import java.math.BigDecimal;

public record SummaryResponse(
        int timeEntries,
        long totalMinutes,
        int moneyEntries,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        String currency
) {
}
