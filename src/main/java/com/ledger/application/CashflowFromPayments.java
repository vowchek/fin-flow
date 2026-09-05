package com.ledger.application;

import com.ledger.domain.StockInstrumentPayment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Suggests annual cashflow fields from stored corporate payment history.
 */
final class CashflowFromPayments {

    private CashflowFromPayments() {
    }

    record Suggestion(BigDecimal annualCashflowPerUnit, BigDecimal cashflowGrowthPct) {
    }

    /**
     * Trailing 12 months sum vs previous 12 months for growth %.
     */
    static Suggestion suggest(List<StockInstrumentPayment> payments, LocalDate asOf) {
        if (payments == null || payments.isEmpty()) {
            return new Suggestion(null, null);
        }
        LocalDate end = asOf == null ? LocalDate.now() : asOf;
        LocalDate startCurrent = end.minusYears(1).plusDays(1);
        LocalDate startPrev = startCurrent.minusYears(1);
        LocalDate endPrev = startCurrent.minusDays(1);

        BigDecimal current = sumInRange(payments, startCurrent, end);
        BigDecimal previous = sumInRange(payments, startPrev, endPrev);

        BigDecimal annual = current.compareTo(BigDecimal.ZERO) > 0 ? current : null;
        BigDecimal growth = null;
        if (annual != null && previous.compareTo(BigDecimal.ZERO) > 0) {
            growth = annual.subtract(previous)
                    .divide(previous, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);
        }
        return new Suggestion(annual, growth);
    }

    private static BigDecimal sumInRange(List<StockInstrumentPayment> payments, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (StockInstrumentPayment p : payments) {
            LocalDate d = p.getOccurredOn();
            if (d == null || d.isBefore(from) || d.isAfter(to)) {
                continue;
            }
            sum = sum.add(p.getAmountPerUnit());
        }
        return sum;
    }
}
