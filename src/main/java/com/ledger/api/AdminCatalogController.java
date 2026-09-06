package com.ledger.api;

import com.ledger.api.dto.InstrumentImportRequest;
import com.ledger.api.dto.InstrumentImportResponse;
import com.ledger.api.dto.InstrumentPaymentResponse;
import com.ledger.api.dto.InstrumentPaymentsRefreshResponse;
import com.ledger.api.dto.InstrumentResponse;
import com.ledger.api.dto.InstrumentUpsertRequest;
import com.ledger.api.dto.PageResponse;
import com.ledger.api.dto.RemoteInstrumentResponse;
import com.ledger.application.CatalogService;
import com.ledger.application.InstrumentImportService;
import com.ledger.domain.AssetMarket;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

    private final CatalogService catalog;
    private final InstrumentImportService imports;

    public AdminCatalogController(CatalogService catalog, InstrumentImportService imports) {
        this.catalog = catalog;
        this.imports = imports;
    }

    @GetMapping("/market/stock/search")
    public List<RemoteInstrumentResponse> searchStock(@RequestParam("q") String q) {
        return imports.searchMarket(q);
    }

    @PostMapping("/stock-instruments/import")
    public InstrumentImportResponse importStock(@Valid @RequestBody InstrumentImportRequest request) {
        return imports.importFromMarket(request);
    }

    @PostMapping("/stock-instruments/refresh-payments")
    public InstrumentPaymentsRefreshResponse refreshAllStockPayments() {
        return imports.refreshAllPayments();
    }

    @PostMapping("/stock-instruments/{id}/refresh")
    public InstrumentImportResponse refreshStock(
            @PathVariable UUID id,
            @RequestParam(value = "overwriteCashflow", required = false) Boolean overwriteCashflow
    ) {
        return imports.refreshFromMarket(id, overwriteCashflow);
    }

    @GetMapping(value = "/stock-instruments/{id}/payments", params = "!page")
    public List<InstrumentPaymentResponse> listStockPayments(@PathVariable UUID id) {
        return imports.listPayments(id);
    }

    @GetMapping(value = "/stock-instruments/{id}/payments", params = "page")
    public PageResponse<InstrumentPaymentResponse> listStockPaymentsPaged(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return imports.listPaymentsPaged(id, page, size);
    }

    @GetMapping(value = "/stock-instruments", params = "!page")
    public List<InstrumentResponse> listStock(@RequestParam(required = false) String q) {
        return catalog.listForAdmin(AssetMarket.MOEX, q);
    }

    @GetMapping(value = "/stock-instruments", params = "page")
    public PageResponse<InstrumentResponse> listStockPaged(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.listForAdminPaged(AssetMarket.MOEX, q, page, size);
    }

    @PostMapping("/stock-instruments")
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentResponse createStock(@Valid @RequestBody InstrumentUpsertRequest request) {
        return catalog.create(AssetMarket.MOEX, request);
    }

    @PutMapping("/stock-instruments/{id}")
    public InstrumentResponse updateStock(@PathVariable UUID id, @Valid @RequestBody InstrumentUpsertRequest request) {
        return catalog.update(AssetMarket.MOEX, id, request);
    }

    @DeleteMapping("/stock-instruments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStock(@PathVariable UUID id) {
        catalog.delete(AssetMarket.MOEX, id);
    }

    @PostMapping(path = "/stock-instruments/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InstrumentResponse uploadStockLogo(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return catalog.uploadLogo(AssetMarket.MOEX, id, file);
    }

    @GetMapping(value = "/crypto-instruments", params = "!page")
    public List<InstrumentResponse> listCrypto(@RequestParam(required = false) String q) {
        return catalog.listForAdmin(AssetMarket.CRYPTO, q);
    }

    @GetMapping(value = "/crypto-instruments", params = "page")
    public PageResponse<InstrumentResponse> listCryptoPaged(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.listForAdminPaged(AssetMarket.CRYPTO, q, page, size);
    }

    @PostMapping("/crypto-instruments")
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentResponse createCrypto(@Valid @RequestBody InstrumentUpsertRequest request) {
        return catalog.create(AssetMarket.CRYPTO, request);
    }

    @PutMapping("/crypto-instruments/{id}")
    public InstrumentResponse updateCrypto(@PathVariable UUID id, @Valid @RequestBody InstrumentUpsertRequest request) {
        return catalog.update(AssetMarket.CRYPTO, id, request);
    }

    @DeleteMapping("/crypto-instruments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCrypto(@PathVariable UUID id) {
        catalog.delete(AssetMarket.CRYPTO, id);
    }

    @PostMapping(path = "/crypto-instruments/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InstrumentResponse uploadCryptoLogo(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return catalog.uploadLogo(AssetMarket.CRYPTO, id, file);
    }
}
