package com.ledger.application;

import com.ledger.api.dto.HoldingResponse;
import com.ledger.domain.AssetMarket;
import com.ledger.domain.CatalogItem;
import com.ledger.domain.TradeSide;
import com.ledger.domain.TxKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class HoldingValuation {

    private HoldingValuation() {
    }

    public record TradeInfo(
            TradeSide side,
            TxKind kind,
            BigDecimal quantity,
            BigDecimal unitPrice,
            LocalDate occurredOn
    ) {
        public TradeInfo(TradeSide side, BigDecimal quantity, BigDecimal unitPrice, LocalDate occurredOn) {
            this(side, TxKind.TRADE, quantity, unitPrice, occurredOn);
        }
    }

    public static HoldingResponse enrich(
            HoldingResponse base,
            AssetMarket market,
            CatalogService catalog,
            QuoteService quotes,
            List<TradeInfo> tradesChronological,
            BigDecimal incomeAbs
    ) {
        return enrich(base, market, catalog, quotes, tradesChronological, incomeAbs, null);
    }

    /**
     * @param liveOverride {@code null} — fetch live quote; non-null Optional — use provided value (empty = no quote).
     */
    public static HoldingResponse enrich(
            HoldingResponse base,
            AssetMarket market,
            CatalogService catalog,
            QuoteService quotes,
            List<TradeInfo> tradesChronological,
            BigDecimal incomeAbs,
            java.util.Optional<QuoteService.LiveQuote> liveOverride
    ) {
        if (base.cash()) {
            return enrichCash(base, market, tradesChronological, incomeAbs);
        }

        CatalogItem item = catalog.findBySymbol(market, base.symbol());
        if (item == null) {
            return withIncome(base, incomeAbs);
        }
        QuoteService.LiveQuote live = liveOverride != null
                ? liveOverride.orElse(null)
                : quotes.getLive(item).orElse(null);

        List<CostBasis.TradeLine> lines = tradesChronological.stream()
                .filter(t -> t.kind() == null || t.kind() == TxKind.TRADE)
                .map(t -> {
                    BigDecimal price = t.unitPrice();
                    if (price == null) {
                        price = quotes.getHistorical(item, t.occurredOn()).orElse(null);
                    }
                    return new CostBasis.TradeLine(t.side(), t.quantity(), price);
                })
                .toList();
        CostBasis.PositionCost positionCost = CostBasis.of(lines);

        BigDecimal unitPrice = live != null ? live.price() : null;
        BigDecimal previous = live != null ? live.previousClose() : null;
        String currency = live != null ? live.currency() : item.currency();
        Instant asOf = live != null ? live.asOf() : null;

        BigDecimal qty = base.quantity();
        BigDecimal marketValue = unitPrice != null ? qty.multiply(unitPrice) : null;
        BigDecimal costBasis = positionCost.costBasis();

        BigDecimal dayAbs = null;
        BigDecimal dayPct = null;
        if (unitPrice != null && previous != null && previous.compareTo(BigDecimal.ZERO) > 0) {
            dayAbs = unitPrice.subtract(previous).multiply(qty);
            dayPct = unitPrice.subtract(previous)
                    .divide(previous, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        BigDecimal totalAbs = null;
        BigDecimal totalPct = null;
        if (marketValue != null && costBasis.compareTo(BigDecimal.ZERO) > 0) {
            totalAbs = marketValue.subtract(costBasis);
            totalPct = totalAbs.divide(costBasis, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        BigDecimal income = incomeAbs == null ? BigDecimal.ZERO : incomeAbs;

        return new HoldingResponse(
                base.id(),
                base.symbol(),
                item.name() != null ? item.name() : base.name(),
                base.quantity(),
                base.openedOn(),
                false,
                item.logoUrl(),
                unitPrice,
                marketValue,
                costBasis.compareTo(BigDecimal.ZERO) > 0 ? costBasis : null,
                dayAbs,
                dayPct,
                totalAbs,
                totalPct,
                income.compareTo(BigDecimal.ZERO) > 0 ? income : null,
                currency,
                asOf,
                base.createdAt(),
                base.updatedAt()
        );
    }

    private static HoldingResponse enrichCash(
            HoldingResponse base,
            AssetMarket market,
            List<TradeInfo> tradesChronological,
            BigDecimal incomeAbs
    ) {
        String currency = CashSupport.currency(market);
        List<CostBasis.TradeLine> lines = tradesChronological.stream()
                .map(t -> {
                    if (t.kind() == TxKind.DIVIDEND || t.kind() == TxKind.COUPON) {
                        // Income increases cash qty without adding to invested cost.
                        return new CostBasis.TradeLine(t.side(), t.quantity(), null);
                    }
                    BigDecimal price = t.unitPrice() != null ? t.unitPrice() : BigDecimal.ONE;
                    return new CostBasis.TradeLine(t.side(), t.quantity(), price);
                })
                .toList();
        CostBasis.PositionCost positionCost = CostBasis.of(lines);
        BigDecimal qty = base.quantity();
        BigDecimal marketValue = qty;
        BigDecimal costBasis = positionCost.costBasis();
        BigDecimal totalAbs = marketValue.subtract(costBasis);
        BigDecimal totalPct = null;
        if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
            totalPct = totalAbs.divide(costBasis, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        } else if (qty.compareTo(BigDecimal.ZERO) > 0) {
            totalPct = BigDecimal.valueOf(100);
        }
        BigDecimal income = incomeAbs == null ? BigDecimal.ZERO : incomeAbs;
        return new HoldingResponse(
                base.id(),
                base.symbol(),
                base.name() != null ? base.name() : CashSupport.name(market),
                qty,
                base.openedOn(),
                true,
                null,
                BigDecimal.ONE,
                marketValue,
                costBasis.compareTo(BigDecimal.ZERO) > 0 ? costBasis : (qty.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ZERO : null),
                null,
                null,
                totalAbs,
                totalPct,
                income.compareTo(BigDecimal.ZERO) > 0 ? income : null,
                currency,
                Instant.now(),
                base.createdAt(),
                base.updatedAt()
        );
    }

    private static HoldingResponse withIncome(HoldingResponse base, BigDecimal incomeAbs) {
        BigDecimal income = incomeAbs == null ? BigDecimal.ZERO : incomeAbs;
        return new HoldingResponse(
                base.id(),
                base.symbol(),
                base.name(),
                base.quantity(),
                base.openedOn(),
                base.cash(),
                base.logoUrl(),
                base.unitPrice(),
                base.marketValue(),
                base.costBasis(),
                base.dayChangeAbs(),
                base.dayChangePct(),
                base.totalChangeAbs(),
                base.totalChangePct(),
                income.compareTo(BigDecimal.ZERO) > 0 ? income : null,
                base.currency(),
                base.priceAsOf(),
                base.createdAt(),
                base.updatedAt()
        );
    }
}
