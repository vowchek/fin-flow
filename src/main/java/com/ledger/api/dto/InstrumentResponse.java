package com.ledger.api.dto;

import java.util.UUID;

public record InstrumentResponse(
        UUID id,
        String market,
        String symbol,
        String externalId,
        String name,
        String logoUrl,
        String currency,
        boolean enabled
) {
}
