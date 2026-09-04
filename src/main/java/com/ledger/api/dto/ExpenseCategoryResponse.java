package com.ledger.api.dto;

import com.ledger.domain.ExpenseCategory;

public record ExpenseCategoryResponse(
        ExpenseCategory code,
        String label
) {
}
