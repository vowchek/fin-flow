package com.ledger.api;

import com.ledger.api.dto.MarketStripItemResponse;
import com.ledger.application.MarketSnapshots;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
@Tag(name = "Market snapshot")
public class MarketSnapshotController {

    private final MarketSnapshots snapshots;

    public MarketSnapshotController(MarketSnapshots snapshots) {
        this.snapshots = snapshots;
    }

    @GetMapping("/stock-strip")
    @Operation(summary = "Полоска индексов для фондового раздела")
    public List<MarketStripItemResponse> stockStrip() {
        return snapshots.stockStrip();
    }

    @GetMapping("/crypto-strip")
    @Operation(summary = "Полоска метрик для криптораздела")
    public List<MarketStripItemResponse> cryptoStrip() {
        return snapshots.cryptoStrip();
    }
}
