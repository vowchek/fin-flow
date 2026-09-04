package com.ledger.application;

import com.ledger.api.dto.HoldingResponse;
import com.ledger.domain.AssetMarket;
import com.ledger.domain.CatalogItem;
import com.ledger.domain.TradeSide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class HoldingValuation {

    private HoldingValuation() {
    }

    public record TradeInfo(TradeSide side, BigDecimal quantity, BigDecimal unitPrice, LocalDate occurredOn) {
    }

    public static HoldingResponse enrich(
            HoldingResponse base,
            AssetMarket market,
            CatalogService catalog,
            QuoteService quotes,
            List<TradeInfo> tradesChronological
    ) {
        CatalogItem item = catalog.findBySymbol(market, base.symbol());
        if (item == null) {
            return base;
        }
        QuoteService.LiveQuote live = quotes.getLive(item).orElse(null);

        List<CostBasis.TradeLine> lines = tradesChronological.stream()
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

        return new HoldingResponse(
                base.id(),
                base.symbol(),
                item.name() != null ? item.name() : base.name(),
                base.quantity(),
                base.openedOn(),
                item.logoUrl(),
                unitPrice,
                marketValue,
                costBasis.compareTo(BigDecimal.ZERO) > 0 ? costBasis : null,
                dayAbs,
                dayPct,
                totalAbs,
                totalPct,
                currency,
                asOf,
                base.createdAt(),
                base.updatedAt()
        );
    }
}
