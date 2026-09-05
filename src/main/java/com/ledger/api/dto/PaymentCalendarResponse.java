package com.ledger.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentCalendarResponse(
        int year,
        List<PaymentCalendarItem> items,
        BigDecimal totalAmount,
        String currency
) {
    public record PaymentCalendarItem(
            String symbol,
            String name,
            String kind,
            LocalDate date,
            BigDecimal perUnit,
            BigDecimal quantity,
            BigDecimal amount,
            String currency
    ) {
    }
}
