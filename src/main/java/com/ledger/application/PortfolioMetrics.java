package com.ledger.application;

import com.ledger.api.dto.HoldingResponse;
import com.ledger.api.dto.PortfolioDetailResponse;
import com.ledger.api.dto.PortfolioSummaryResponse;
import com.ledger.api.dto.ValuePointResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class PortfolioMetrics {

    private PortfolioMetrics() {
    }

    record Totals(
            BigDecimal totalValue,
            BigDecimal dayChangeAbs,
            BigDecimal dayChangePct,
            BigDecimal totalChangeAbs,
            BigDecimal totalChangePct,
            String currency
    ) {
    }

    static Totals of(List<HoldingResponse> holdings, String defaultCurrency) {
        BigDecimal value = BigDecimal.ZERO;
        BigDecimal dayAbs = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        boolean anyValue = false;
        boolean anyDay = false;
        boolean anyCost = false;
        String currency = defaultCurrency;
        for (HoldingResponse h : holdings) {
            if (h.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (h.currency() != null) {
                currency = h.currency();
            }
            if (h.marketValue() != null) {
                value = value.add(h.marketValue());
                anyValue = true;
            }
            if (h.dayChangeAbs() != null) {
                dayAbs = dayAbs.add(h.dayChangeAbs());
                anyDay = true;
            }
            if (h.costBasis() != null) {
                cost = cost.add(h.costBasis());
                anyCost = true;
            }
        }
        BigDecimal dayPct = null;
        if (anyDay && anyValue) {
            BigDecimal prevValue = value.subtract(dayAbs);
            if (prevValue.compareTo(BigDecimal.ZERO) > 0) {
                dayPct = dayAbs.divide(prevValue, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }
        }
        BigDecimal totalAbs = null;
        BigDecimal totalPct = null;
        if (anyValue && anyCost && cost.compareTo(BigDecimal.ZERO) > 0) {
            totalAbs = value.subtract(cost);
            totalPct = totalAbs.divide(cost, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }
        return new Totals(
                anyValue ? value : null,
                anyDay ? dayAbs : null,
                dayPct,
                totalAbs,
                totalPct,
                currency
        );
    }

    static PortfolioSummaryResponse summary(
            UUID id,
            String name,
            String description,
            int holdingsCount,
            List<HoldingResponse> holdings,
            String defaultCurrency,
            Instant createdAt,
            Instant updatedAt
    ) {
        Totals totals = of(holdings, defaultCurrency);
        return new PortfolioSummaryResponse(
                id,
                name,
                description,
                holdingsCount,
                holdings.stream()
                        .filter(h -> h.quantity().compareTo(BigDecimal.ZERO) > 0)
                        .toList(),
                totals.totalValue(),
                totals.dayChangeAbs(),
                totals.dayChangePct(),
                totals.totalChangeAbs(),
                totals.totalChangePct(),
                totals.currency(),
                createdAt,
                updatedAt
        );
    }

    static PortfolioDetailResponse detail(
            UUID id,
            String name,
            String description,
            List<HoldingResponse> holdings,
            List<ValuePointResponse> valueHistory,
            String defaultCurrency,
            Instant createdAt,
            Instant updatedAt
    ) {
        Totals totals = of(holdings, defaultCurrency);
        return new PortfolioDetailResponse(
                id,
                name,
                description,
                holdings,
                valueHistory,
                totals.totalValue(),
                totals.dayChangeAbs(),
                totals.dayChangePct(),
                totals.totalChangeAbs(),
                totals.totalChangePct(),
                totals.currency(),
                createdAt,
                updatedAt
        );
    }
}
