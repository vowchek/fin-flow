package com.ledger.api;

import com.ledger.api.dto.InstrumentResponse;
import com.ledger.application.CatalogService;
import com.ledger.domain.AssetMarket;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/stock-instruments")
    public List<InstrumentResponse> stockInstruments(@RequestParam(required = false) String q) {
        return catalog.listForUsers(AssetMarket.MOEX, q);
    }

    @GetMapping("/crypto-instruments")
    public List<InstrumentResponse> cryptoInstruments(@RequestParam(required = false) String q) {
        return catalog.listForUsers(AssetMarket.CRYPTO, q);
    }
}
