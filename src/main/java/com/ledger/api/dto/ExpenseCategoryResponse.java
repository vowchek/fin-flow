package com.ledger.api.dto;

import java.util.UUID;

public record ExpenseCategoryResponse(
        UUID id,
        String name,
        int displayOrder
) {
}
