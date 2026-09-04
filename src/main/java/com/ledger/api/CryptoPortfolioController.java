package com.ledger.api;

import com.ledger.api.dto.HoldingCreateRequest;
import com.ledger.api.dto.HoldingResponse;
import com.ledger.api.dto.PortfolioDetailResponse;
import com.ledger.api.dto.PortfolioRequest;
import com.ledger.api.dto.PortfolioSummaryResponse;
import com.ledger.api.dto.TradeRequest;
import com.ledger.api.dto.TradeResponse;
import com.ledger.application.CryptoPortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crypto-portfolios")
@Tag(name = "Crypto portfolios")
public class CryptoPortfolioController {

    private final CryptoPortfolioService portfolios;

    public CryptoPortfolioController(CryptoPortfolioService portfolios) {
        this.portfolios = portfolios;
    }

    @GetMapping
    @Operation(summary = "Список криптопортфелей")
    public List<PortfolioSummaryResponse> list() {
        return portfolios.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Портфель с позициями")
    public PortfolioDetailResponse get(@PathVariable UUID id) {
        return portfolios.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioDetailResponse create(@Valid @RequestBody PortfolioRequest request) {
        return portfolios.create(request);
    }

    @PutMapping("/{id}")
    public PortfolioDetailResponse update(@PathVariable UUID id, @Valid @RequestBody PortfolioRequest request) {
        return portfolios.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        portfolios.delete(id);
    }

    @PostMapping("/{id}/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Открыть позицию (первая покупка)")
    public HoldingResponse openHolding(@PathVariable UUID id, @Valid @RequestBody HoldingCreateRequest request) {
        return portfolios.openHolding(id, request);
    }

    @PostMapping("/{id}/holdings/{holdingId}/buys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Купить")
    public HoldingResponse buy(
            @PathVariable UUID id,
            @PathVariable UUID holdingId,
            @Valid @RequestBody TradeRequest request
    ) {
        return portfolios.buy(id, holdingId, request);
    }

    @PostMapping("/{id}/holdings/{holdingId}/sells")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Продать")
    public HoldingResponse sell(
            @PathVariable UUID id,
            @PathVariable UUID holdingId,
            @Valid @RequestBody TradeRequest request
    ) {
        return portfolios.sell(id, holdingId, request);
    }

    @GetMapping("/{id}/holdings/{holdingId}/transactions")
    @Operation(summary = "История сделок по позиции")
    public List<TradeResponse> transactions(@PathVariable UUID id, @PathVariable UUID holdingId) {
        return portfolios.listTransactions(id, holdingId);
    }
}
