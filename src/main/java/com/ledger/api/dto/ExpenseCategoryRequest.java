package com.ledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryRequest(
        @NotBlank @Size(max = 100) String name
) {
}
