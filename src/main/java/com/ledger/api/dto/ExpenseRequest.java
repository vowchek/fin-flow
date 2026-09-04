package com.ledger.api.dto;

import com.ledger.domain.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ExpenseRequest(
        @NotNull ExpenseCategory category,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String yearMonth,
        @Size(max = 1000) String note
) {
}
