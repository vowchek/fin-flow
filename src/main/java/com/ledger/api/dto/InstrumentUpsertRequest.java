package com.ledger.api.dto;

import com.ledger.domain.AssetKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstrumentUpsertRequest(
        @NotBlank @Size(max = 32) String symbol,
        @NotBlank @Size(max = 120) String externalId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 8) String currency,
        Boolean enabled,
        AssetKind assetKind,
        /** null — не менять при update / true при create */
        Boolean paysDividends
) {
}
