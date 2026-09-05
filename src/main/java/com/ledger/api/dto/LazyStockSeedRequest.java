package com.ledger.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record LazyStockSeedRequest(
        @NotEmpty List<@Valid HoldingCreateRequest> holdings
) {
}
