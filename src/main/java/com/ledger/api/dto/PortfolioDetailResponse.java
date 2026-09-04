package com.ledger.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PortfolioDetailResponse(
        UUID id,
        String name,
        String description,
        List<HoldingResponse> holdings,
        List<ValuePointResponse> valueHistory,
        BigDecimal totalValue,
        BigDecimal dayChangeAbs,
        BigDecimal dayChangePct,
        BigDecimal totalChangeAbs,
        BigDecimal totalChangePct,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}
