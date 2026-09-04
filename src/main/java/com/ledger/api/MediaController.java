package com.ledger.api;

import com.ledger.application.CatalogService;
import com.ledger.domain.AssetMarket;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final CatalogService catalog;

    public MediaController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/stock-instruments/{id}/logo")
    public ResponseEntity<Resource> stockLogo(@PathVariable UUID id) throws Exception {
        return logo(AssetMarket.MOEX, id);
    }

    @GetMapping("/crypto-instruments/{id}/logo")
    public ResponseEntity<Resource> cryptoLogo(@PathVariable UUID id) throws Exception {
        return logo(AssetMarket.CRYPTO, id);
    }

    private ResponseEntity<Resource> logo(AssetMarket market, UUID id) throws Exception {
        CatalogService.LogoFile file = catalog.loadLogo(market, id);
        String contentType = Files.probeContentType(file.path());
        if (contentType == null) {
            String name = file.filename().toLowerCase(Locale.ROOT);
            if (name.endsWith(".svg")) {
                contentType = "image/svg+xml";
            } else if (name.endsWith(".webp")) {
                contentType = "image/webp";
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else {
                contentType = "image/png";
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(file.path()));
    }
}
