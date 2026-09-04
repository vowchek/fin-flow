package com.ledger.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
