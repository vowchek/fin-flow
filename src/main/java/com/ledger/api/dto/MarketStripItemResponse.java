package com.ledger.api.dto;

import java.math.BigDecimal;

public record MarketStripItemResponse(
        String id,
        String label,
        BigDecimal value,
        BigDecimal changePct,
        String displayPrefix,
        String displaySuffix
) {
}
