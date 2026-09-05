package com.ledger.application;

import com.ledger.domain.PortfolioEntryMode;

final class PortfolioEntryModes {

    private PortfolioEntryModes() {
    }

    static PortfolioEntryMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PortfolioEntryMode.MANUAL;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "LAZY" -> PortfolioEntryMode.LAZY;
            case "MANUAL" -> PortfolioEntryMode.MANUAL;
            case "BROKER_REPORT" -> PortfolioEntryMode.BROKER_REPORT;
            case "BROKER_API" -> PortfolioEntryMode.BROKER_API;
            default -> throw new IllegalArgumentException("Unknown entryMode: " + raw);
        };
    }
}
