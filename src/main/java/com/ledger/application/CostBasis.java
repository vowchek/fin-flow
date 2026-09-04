package com.ledger.application;

import com.ledger.domain.TradeSide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class CostBasis {

    private CostBasis() {
    }

    public record TradeLine(TradeSide side, BigDecimal quantity, BigDecimal unitPrice) {
    }

    public record PositionCost(BigDecimal quantity, BigDecimal costBasis) {
    }

    public static PositionCost of(List<TradeLine> trades) {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (TradeLine trade : trades) {
            if (trade.unitPrice() == null) {
                continue;
            }
            if (trade.side() == TradeSide.BUY) {
                cost = cost.add(trade.quantity().multiply(trade.unitPrice()));
                qty = qty.add(trade.quantity());
            } else if (qty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal avg = cost.divide(qty, 8, RoundingMode.HALF_UP);
                BigDecimal sellQty = trade.quantity().min(qty);
                cost = cost.subtract(avg.multiply(sellQty));
                qty = qty.subtract(sellQty);
                if (cost.compareTo(BigDecimal.ZERO) < 0) {
                    cost = BigDecimal.ZERO;
                }
            }
        }
        return new PositionCost(qty, cost.max(BigDecimal.ZERO));
    }
}
