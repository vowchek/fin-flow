package com.ledger.application;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

final class YearMonths {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private YearMonths() {
    }

    static YearMonth parse(String value) {
        if (value == null || value.isBlank()) {
            throw new DateTimeException("year-month is required (yyyy-MM)");
        }
        try {
            return YearMonth.parse(value.trim(), FORMAT);
        } catch (DateTimeParseException ex) {
            throw new DateTimeException("Invalid year-month: " + value + " (expected yyyy-MM)");
        }
    }

    static LocalDate toFirstDay(YearMonth month) {
        return month.atDay(1);
    }

    static LocalDate toFirstDay(String yearMonth) {
        return toFirstDay(parse(yearMonth));
    }

    static String format(LocalDate firstDayOfMonth) {
        return YearMonth.from(firstDayOfMonth).format(FORMAT);
    }
}
