package com.ledger.application;

import java.util.Locale;

final class Symbols {

    private Symbols() {
    }

    static String normalize(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
