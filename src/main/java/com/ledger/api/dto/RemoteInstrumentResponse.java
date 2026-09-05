package com.ledger.api.dto;

public record RemoteInstrumentResponse(
        String symbol,
        String externalId,
        String name,
        String currency,
        String logoUrl
) {
}
