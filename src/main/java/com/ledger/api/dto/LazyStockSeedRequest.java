package com.ledger.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record LazyStockSeedRequest(
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal investedAmount,
        @NotEmpty List<@Valid HoldingCreateRequest> holdings
) {
}
