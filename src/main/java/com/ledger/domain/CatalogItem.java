package com.ledger.domain;

import java.util.UUID;

/** Read model for catalog rows used by quotes and portfolio valuation. */
public record CatalogItem(
        AssetMarket market,
        UUID id,
        String symbol,
        String externalId,
        String name,
        String currency,
        String logoUrl,
        boolean enabled
) {
}
