package com.ledger.application;

import com.ledger.api.dto.MarketStripItemResponse;

import java.util.List;

public interface MarketSnapshots {
    List<MarketStripItemResponse> stockStrip();

    List<MarketStripItemResponse> cryptoStrip();
}
