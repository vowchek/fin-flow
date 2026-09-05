package com.ledger.application;

import com.ledger.domain.StockInstrument;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;

final class PassiveIncomeCalc {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private PassiveIncomeCalc() {
    }

    static int baseYear() {
        return LocalDate.now().getYear();
    }

    static BigDecimal grossForHolding(
            BigDecimal quantity,
            StockInstrument instrument,
            int year,
            int baseYear
    ) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0 || instrument == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal perUnit = instrument.getAnnualCashflowPerUnit();
        if (perUnit == null || perUnit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        Integer until = instrument.getCashflowUntilYear();
        if (until != null && year > until) {
            return BigDecimal.ZERO;
        }
        BigDecimal growth = instrument.getCashflowGrowthPct();
        if (growth == null) {
            growth = BigDecimal.ZERO;
        }
        int delta = year - baseYear;
        BigDecimal factor = BigDecimal.ONE;
        if (delta != 0 && growth.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal rate = BigDecimal.ONE.add(growth.divide(BigDecimal.valueOf(100), MC));
            factor = rate.pow(Math.abs(delta), MC);
            if (delta < 0) {
                factor = BigDecimal.ONE.divide(factor, MC);
            }
        }
        return quantity.multiply(perUnit, MC).multiply(factor, MC);
    }

    static BigDecimal afterTax(BigDecimal gross, BigDecimal taxRatePercent) {
        BigDecimal tax = taxRatePercent == null ? BigDecimal.ZERO : taxRatePercent;
        if (tax.compareTo(BigDecimal.ZERO) < 0) {
            tax = BigDecimal.ZERO;
        }
        if (tax.compareTo(BigDecimal.valueOf(100)) > 0) {
            tax = BigDecimal.valueOf(100);
        }
        BigDecimal keep = BigDecimal.ONE.subtract(tax.divide(BigDecimal.valueOf(100), MC));
        return gross.multiply(keep, MC);
    }

    static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
