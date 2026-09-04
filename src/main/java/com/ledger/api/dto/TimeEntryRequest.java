package com.ledger.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record TimeEntryRequest(
        UUID projectId,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        String note
) {
}
