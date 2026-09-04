package com.ledger.api;

import com.ledger.api.dto.InstrumentResponse;
import com.ledger.api.dto.InstrumentUpsertRequest;
import com.ledger.application.CatalogService;
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

    public AdminCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/stock-instruments")
    public List<InstrumentResponse> listStock() {
        return catalog.listForAdmin(AssetMarket.MOEX);
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

    @GetMapping("/crypto-instruments")
    public List<InstrumentResponse> listCrypto() {
        return catalog.listForAdmin(AssetMarket.CRYPTO);
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
