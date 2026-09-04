package com.ledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstrumentUpsertRequest(
        @NotBlank @Size(max = 32) String symbol,
        @NotBlank @Size(max = 120) String externalId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 8) String currency,
        Boolean enabled
) {
}
