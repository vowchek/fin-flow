package com.ledger.api.dto;

import com.ledger.domain.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TradeResponse(
        UUID id,
        TradeSide side,
        BigDecimal quantity,
        LocalDate occurredOn,
        BigDecimal unitPrice,
        String note,
        Instant createdAt
) {
}
