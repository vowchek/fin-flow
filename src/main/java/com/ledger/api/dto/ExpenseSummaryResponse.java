package com.ledger.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ExpenseSummaryResponse(
        Map<String, Map<String, BigDecimal>> byCategoryByMonth,
        Map<String, BigDecimal> totalsByCategory,
        BigDecimal total
) {
}
