package com.ledger.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LazyCryptoSeedRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal investedAmount,
        @NotNull LocalDate startedOn,
        @NotEmpty List<@Valid Item> holdings
) {
    public record Item(
            UUID instrumentId,
            @Size(max = 32) String symbol,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity
    ) {
    }
}
