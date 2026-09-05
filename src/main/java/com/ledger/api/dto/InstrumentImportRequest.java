package com.ledger.api.dto;

import jakarta.validation.constraints.Size;

public record InstrumentImportRequest(
        @Size(max = 120) String externalId,
        @Size(max = 32) String symbol,
        Boolean enabled,
        /** When true (default), rewrite annual cashflow fields from payment history. */
        Boolean overwriteCashflow
) {
}
