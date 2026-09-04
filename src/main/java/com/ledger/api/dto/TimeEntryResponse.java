package com.ledger.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TimeEntryResponse(
        UUID id,
        UUID projectId,
        Instant startedAt,
        Instant endedAt,
        int durationMinutes,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
