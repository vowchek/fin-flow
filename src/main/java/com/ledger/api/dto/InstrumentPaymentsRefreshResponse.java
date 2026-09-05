package com.ledger.api.dto;

import java.util.List;
import java.util.UUID;

public record InstrumentPaymentsRefreshResponse(
        int refreshed,
        int failed,
        List<Item> results
) {
    public record Item(
            UUID id,
            String symbol,
            boolean ok,
            String error,
            int paymentCount
    ) {
    }
}
