package com.ledger.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ValuePointResponse(
        LocalDate date,
        BigDecimal value,
        BigDecimal invested
) {
}
