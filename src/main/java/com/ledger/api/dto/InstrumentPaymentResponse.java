package com.ledger.api.dto;

import com.ledger.domain.InstrumentPaymentKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstrumentPaymentResponse(
        UUID id,
        LocalDate occurredOn,
        BigDecimal amountPerUnit,
        String currency,
        InstrumentPaymentKind kind
) {
}
