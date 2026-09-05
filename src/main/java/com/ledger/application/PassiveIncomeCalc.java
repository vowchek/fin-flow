package com.ledger.application;

import com.ledger.domain.AssetKind;
import com.ledger.domain.InstrumentPaymentKind;
import com.ledger.domain.StockInstrumentPayment;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Passive income from {@code stock_instrument_payment}.
 * <p>
 * Calendar year of payment/record date (no Jan–Feb shift).
 * Current year: if the usual number of payments for the year is already in,
 * use YTD as <em>actual</em> — do not invent a second dividend (MOEX pays yearly).
 * Multi-payer names (e.g. X5) stay on forecast until that count is reached.
 * Future years: last known run-rate × median YoY (outliers capped).
 */
final class PassiveIncomeCalc {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private PassiveIncomeCalc() {
    }

    enum Basis {
        ACTUAL,
        FORECAST,
        NONE
    }

    record YearEstimate(BigDecimal perUnit, Basis basis, BigDecimal growthPctUsed) {
        static YearEstimate zero() {
            return new YearEstimate(BigDecimal.ZERO, Basis.NONE, null);
        }
    }

    static int baseYear() {
        return LocalDate.now().getYear();
    }

    /** Calendar year of the payment date (dividends and coupons). */
    static int dividendYear(LocalDate date, InstrumentPaymentKind kind) {
        if (date == null) {
            return 0;
        }
        return date.getYear();
    }

    static NavigableMap<Integer, BigDecimal> annualTotals(List<StockInstrumentPayment> payments) {
        NavigableMap<Integer, BigDecimal> byYear = new TreeMap<>();
        if (payments == null) {
            return byYear;
        }
        for (StockInstrumentPayment p : payments) {
            if (p.getAmountPerUnit() == null || p.getAmountPerUnit().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int y = dividendYear(p.getOccurredOn(), p.getKind());
            if (y <= 0) {
                continue;
            }
            byYear.merge(y, p.getAmountPerUnit(), BigDecimal::add);
        }
        return byYear;
    }

    static int countPaymentsInYear(List<StockInstrumentPayment> payments, int year) {
        if (payments == null) {
            return 0;
        }
        int n = 0;
        for (StockInstrumentPayment p : payments) {
            if (p.getAmountPerUnit() == null || p.getAmountPerUnit().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (dividendYear(p.getOccurredOn(), p.getKind()) == year) {
                n++;
            }
        }
        return n;
    }

    /**
     * Median number of positive payments per calendar year in history (before asOfYear).
     * Defaults to 1 (typical Russian equity annual dividend).
     */
    static int typicalPaymentsPerYear(List<StockInstrumentPayment> payments, int asOfYear) {
        if (payments == null || payments.isEmpty()) {
            return 1;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (StockInstrumentPayment p : payments) {
            if (p.getAmountPerUnit() == null || p.getAmountPerUnit().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int y = dividendYear(p.getOccurredOn(), p.getKind());
            if (y <= 0 || y >= asOfYear) {
                continue;
            }
            counts.merge(y, 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            return 1;
        }
        List<Integer> values = new ArrayList<>(counts.values());
        Collections.sort(values);
        return values.get(values.size() / 2);
    }

    /**
     * Sum of payments with occurredOn in (asOf − 1 year, asOf].
     */
    static BigDecimal trailingTwelveMonths(List<StockInstrumentPayment> payments, LocalDate asOf) {
        if (payments == null || payments.isEmpty() || asOf == null) {
            return BigDecimal.ZERO;
        }
        LocalDate fromExclusive = asOf.minusYears(1);
        BigDecimal sum = BigDecimal.ZERO;
        for (StockInstrumentPayment p : payments) {
            LocalDate d = p.getOccurredOn();
            if (d == null || d.isAfter(asOf) || !d.isAfter(fromExclusive)) {
                continue;
            }
            if (p.getAmountPerUnit() == null || p.getAmountPerUnit().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            sum = sum.add(p.getAmountPerUnit());
        }
        return sum;
    }

    /**
     * Median YoY growth as a fraction. Consecutive years only; |jump| &gt; 100% skipped.
     */
    static BigDecimal medianYoYGrowth(NavigableMap<Integer, BigDecimal> annual) {
        if (annual == null || annual.size() < 2) {
            return BigDecimal.ZERO;
        }
        List<Integer> years = new ArrayList<>(annual.keySet());
        List<BigDecimal> rates = new ArrayList<>();
        for (int i = 1; i < years.size(); i++) {
            int prevYear = years.get(i - 1);
            int curYear = years.get(i);
            if (curYear - prevYear != 1) {
                continue;
            }
            BigDecimal prev = annual.get(prevYear);
            BigDecimal cur = annual.get(curYear);
            if (prev == null || cur == null || prev.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal rate = cur.subtract(prev, MC).divide(prev, MC);
            if (rate.compareTo(BigDecimal.ONE) > 0 || rate.compareTo(BigDecimal.valueOf(-0.8)) < 0) {
                continue;
            }
            rates.add(rate);
        }
        if (rates.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Collections.sort(rates);
        int n = rates.size();
        if (n % 2 == 1) {
            return rates.get(n / 2);
        }
        return rates.get(n / 2 - 1).add(rates.get(n / 2), MC).divide(BigDecimal.valueOf(2), MC);
    }

    static YearEstimate perUnitForYear(
            List<StockInstrumentPayment> payments,
            AssetKind assetKind,
            boolean paysDividends,
            int year,
            int asOfYear
    ) {
        return perUnitForYear(payments, assetKind, paysDividends, year, asOfYear, LocalDate.now());
    }

    static YearEstimate perUnitForYear(
            List<StockInstrumentPayment> payments,
            AssetKind assetKind,
            boolean paysDividends,
            int year,
            int asOfYear,
            LocalDate asOfDate
    ) {
        if (!paysDividends && year >= asOfYear) {
            return YearEstimate.zero();
        }

        NavigableMap<Integer, BigDecimal> annual = annualTotals(payments);
        if (annual.isEmpty()) {
            return YearEstimate.zero();
        }

        LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;

        if (assetKind == AssetKind.BOND) {
            BigDecimal sum = annual.getOrDefault(year, BigDecimal.ZERO);
            if (sum.compareTo(BigDecimal.ZERO) > 0) {
                return new YearEstimate(sum, year < asOfYear ? Basis.ACTUAL : Basis.FORECAST, null);
            }
            if (year < asOfYear) {
                return YearEstimate.zero();
            }
            BigDecimal ttm = trailingTwelveMonths(payments, asOf);
            if (ttm.compareTo(BigDecimal.ZERO) > 0) {
                return new YearEstimate(ttm, Basis.FORECAST, BigDecimal.ZERO);
            }
            Map.Entry<Integer, BigDecimal> last = annual.floorEntry(asOfYear - 1);
            if (last == null || last.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                return YearEstimate.zero();
            }
            return new YearEstimate(last.getValue(), Basis.FORECAST, BigDecimal.ZERO);
        }

        if (year < asOfYear) {
            BigDecimal actual = annual.getOrDefault(year, BigDecimal.ZERO);
            if (actual.compareTo(BigDecimal.ZERO) <= 0) {
                return YearEstimate.zero();
            }
            return new YearEstimate(actual, Basis.ACTUAL, null);
        }

        int typical = Math.max(1, typicalPaymentsPerYear(payments, asOfYear));
        int ytdCount = countPaymentsInYear(payments, asOfYear);
        BigDecimal ytd = annual.getOrDefault(asOfYear, BigDecimal.ZERO);
        boolean yearComplete = ytdCount >= typical && ytd.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal ttm = trailingTwelveMonths(payments, asOf);
        NavigableMap<Integer, BigDecimal> growthHistory = new TreeMap<>(annual.headMap(asOfYear, false));
        if (yearComplete) {
            growthHistory.put(asOfYear, ytd);
        }
        BigDecimal growth = medianYoYGrowth(growthHistory);
        BigDecimal growthPct = growth.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);

        if (year == asOfYear) {
            if (yearComplete) {
                // Annual payer already paid (e.g. MOEX 19.57) — that is the year, not a forecast.
                return new YearEstimate(ytd, Basis.ACTUAL, null);
            }
            if (ytd.compareTo(BigDecimal.ZERO) > 0) {
                // Interim only (e.g. X5 after 9M) — expect the rest; floor at TTM.
                BigDecimal expected = ttm.max(ytd);
                if (expected.compareTo(ytd) <= 0) {
                    return new YearEstimate(ytd, Basis.ACTUAL, null);
                }
                return new YearEstimate(expected, Basis.FORECAST, growthPct);
            }
            BigDecimal base = ttm.compareTo(BigDecimal.ZERO) > 0
                    ? ttm
                    : annual.getOrDefault(asOfYear - 1, BigDecimal.ZERO);
            if (base.compareTo(BigDecimal.ZERO) <= 0) {
                return YearEstimate.zero();
            }
            return new YearEstimate(base, Basis.FORECAST, growthPct);
        }

        // Future: run-rate from completed current year, else TTM / last year.
        BigDecimal base;
        if (yearComplete) {
            base = ytd;
        } else if (ttm.compareTo(BigDecimal.ZERO) > 0) {
            base = ttm;
        } else {
            Map.Entry<Integer, BigDecimal> last = annual.floorEntry(asOfYear - 1);
            base = last == null ? BigDecimal.ZERO : last.getValue();
        }
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return YearEstimate.zero();
        }
        int steps = Math.max(0, year - asOfYear);
        BigDecimal factor = BigDecimal.ONE;
        if (steps != 0 && growth.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal rate = BigDecimal.ONE.add(growth, MC);
            if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                return new YearEstimate(BigDecimal.ZERO, Basis.FORECAST, growthPct);
            }
            factor = rate.pow(steps, MC);
        }
        return new YearEstimate(base.multiply(factor, MC).max(BigDecimal.ZERO), Basis.FORECAST, growthPct);
    }

    static BigDecimal grossForHolding(
            BigDecimal quantity,
            List<StockInstrumentPayment> payments,
            AssetKind assetKind,
            boolean paysDividends,
            int year,
            int asOfYear
    ) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        YearEstimate estimate = perUnitForYear(payments, assetKind, paysDividends, year, asOfYear);
        return quantity.multiply(estimate.perUnit(), MC);
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
