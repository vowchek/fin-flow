package com.ledger.api.dto;

import com.ledger.domain.TradeSide;
import com.ledger.domain.TxKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TradeResponse(
        UUID id,
        TradeSide side,
        TxKind kind,
        BigDecimal quantity,
        LocalDate occurredOn,
        BigDecimal unitPrice,
        String note,
        boolean settleToCash,
        Instant createdAt
) {
}
