package com.ledger.api.dto;

import com.ledger.domain.PortfolioEntryMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PortfolioSummaryResponse(
        UUID id,
        String name,
        String description,
        PortfolioEntryMode entryMode,
        int holdingsCount,
        List<HoldingResponse> holdings,
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
