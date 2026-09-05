package com.ledger.application;

import com.ledger.domain.AssetMarket;

public final class CashSupport {

    private CashSupport() {
    }

    public static String symbol(AssetMarket market) {
        return market == AssetMarket.CRYPTO ? "USD" : "RUB";
    }

    public static String name(AssetMarket market) {
        return market == AssetMarket.CRYPTO ? "Доллары" : "Рубли";
    }

    public static String currency(AssetMarket market) {
        return symbol(market);
    }
}
