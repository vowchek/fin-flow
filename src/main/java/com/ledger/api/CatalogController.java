package com.ledger.api;

import com.ledger.api.dto.InstrumentResponse;
import com.ledger.api.dto.PageResponse;
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

    @GetMapping(value = "/stock-instruments", params = "!page")
    public List<InstrumentResponse> stockInstruments(@RequestParam(required = false) String q) {
        return catalog.listForUsers(AssetMarket.MOEX, q);
    }

    @GetMapping(value = "/stock-instruments", params = "page")
    public PageResponse<InstrumentResponse> stockInstrumentsPaged(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.listForUsersPaged(AssetMarket.MOEX, q, page, size);
    }

    @GetMapping(value = "/crypto-instruments", params = "!page")
    public List<InstrumentResponse> cryptoInstruments(@RequestParam(required = false) String q) {
        return catalog.listForUsers(AssetMarket.CRYPTO, q);
    }

    @GetMapping(value = "/crypto-instruments", params = "page")
    public PageResponse<InstrumentResponse> cryptoInstrumentsPaged(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.listForUsersPaged(AssetMarket.CRYPTO, q, page, size);
    }
}
