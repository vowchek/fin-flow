package com.ledger.application;

import com.ledger.api.dto.HoldingResponse;
import com.ledger.api.dto.PortfolioDetailResponse;
import com.ledger.api.dto.PortfolioSummaryResponse;
import com.ledger.api.dto.ValuePointResponse;
import com.ledger.domain.PortfolioEntryMode;

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

    static Totals of(List<HoldingResponse> holdings, String defaultCurrency, BigDecimal investedAmount) {
        BigDecimal value = BigDecimal.ZERO;
        BigDecimal dayAbs = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        // Asset P&L only: price move + dividends/coupons (same as table rows).
        // Cash free money from settled income is attributed via incomeAbs on the asset — do not
        // also count cash (MV − cost), or settled dividends would be double-counted.
        BigDecimal profit = BigDecimal.ZERO;
        boolean anyValue = false;
        boolean anyDay = false;
        boolean anyCost = false;
        boolean anyProfit = false;
        String currency = defaultCurrency;
        for (HoldingResponse h : holdings) {
            boolean open = h.quantity().compareTo(BigDecimal.ZERO) > 0;
            if (h.currency() != null && open) {
                currency = h.currency();
            }
            if (open) {
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
            if (!h.cash()) {
                BigDecimal price = h.totalChangeAbs() != null ? h.totalChangeAbs() : BigDecimal.ZERO;
                BigDecimal income = h.incomeAbs() != null ? h.incomeAbs() : BigDecimal.ZERO;
                if (h.totalChangeAbs() != null || h.incomeAbs() != null) {
                    profit = profit.add(price).add(income);
                    anyProfit = true;
                }
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
        // Portfolio P&L: market value − cash invested (same as detail «Прибыль»).
        if (investedAmount != null && investedAmount.compareTo(BigDecimal.ZERO) > 0 && anyValue) {
            totalAbs = value.subtract(investedAmount);
            totalPct = totalAbs.divide(investedAmount, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        } else if (anyProfit) {
            // Fallback when invested is unset: asset price move + dividends/coupons.
            totalAbs = profit;
            if (anyCost && cost.compareTo(BigDecimal.ZERO) > 0) {
                totalPct = totalAbs.divide(cost, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }
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
            PortfolioEntryMode entryMode,
            int holdingsCount,
            List<HoldingResponse> holdings,
            String defaultCurrency,
            BigDecimal investedAmount,
            Instant createdAt,
            Instant updatedAt
    ) {
        Totals totals = of(holdings, defaultCurrency, investedAmount);
        return new PortfolioSummaryResponse(
                id,
                name,
                description,
                entryMode == null ? PortfolioEntryMode.MANUAL : entryMode,
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
            PortfolioEntryMode entryMode,
            List<HoldingResponse> holdings,
            List<ValuePointResponse> valueHistory,
            String defaultCurrency,
            BigDecimal taxRatePercent,
            BigDecimal investedAmount,
            Instant createdAt,
            Instant updatedAt
    ) {
        Totals totals = of(holdings, defaultCurrency, investedAmount);
        return new PortfolioDetailResponse(
                id,
                name,
                description,
                entryMode == null ? PortfolioEntryMode.MANUAL : entryMode,
                holdings,
                valueHistory,
                totals.totalValue(),
                totals.dayChangeAbs(),
                totals.dayChangePct(),
                totals.totalChangeAbs(),
                totals.totalChangePct(),
                totals.currency(),
                taxRatePercent,
                investedAmount,
                createdAt,
                updatedAt
        );
    }
}
