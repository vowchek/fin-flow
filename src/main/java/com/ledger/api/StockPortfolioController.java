package com.ledger.api;

import com.ledger.api.dto.CashMovementRequest;
import com.ledger.api.dto.HoldingCashflowRequest;
import com.ledger.api.dto.HoldingCashflowResponse;
import com.ledger.api.dto.HoldingCreateRequest;
import com.ledger.api.dto.HoldingResponse;
import com.ledger.api.dto.LazyStockSeedRequest;
import com.ledger.api.dto.PassiveIncomeResponse;
import com.ledger.api.dto.PaymentCalendarResponse;
import com.ledger.api.dto.PortfolioDetailResponse;
import com.ledger.api.dto.PortfolioRequest;
import com.ledger.api.dto.PortfolioSettingsRequest;
import com.ledger.api.dto.PortfolioSettingsResponse;
import com.ledger.api.dto.PortfolioSummaryResponse;
import com.ledger.api.dto.TradeRequest;
import com.ledger.api.dto.TradeResponse;
import com.ledger.api.dto.ValuePointResponse;
import com.ledger.application.StockPortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-portfolios")
@Tag(name = "Stock portfolios")
public class StockPortfolioController {

    private final StockPortfolioService portfolios;

    public StockPortfolioController(StockPortfolioService portfolios) {
        this.portfolios = portfolios;
    }

    @GetMapping
    @Operation(summary = "Список фондовых портфелей")
    public List<PortfolioSummaryResponse> list() {
        return portfolios.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Портфель с позициями")
    public PortfolioDetailResponse get(@PathVariable UUID id) {
        return portfolios.get(id);
    }

    @GetMapping("/{id}/value-history")
    @Operation(summary = "График стоимости портфеля по дням")
    public List<ValuePointResponse> valueHistory(@PathVariable UUID id) {
        return portfolios.valueHistory(id);
    }

    @GetMapping("/{id}/passive-income")
    @Operation(summary = "Прогноз пассивного дохода за год")
    public PassiveIncomeResponse passiveIncome(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer year
    ) {
        return portfolios.passiveIncome(id, year);
    }

    @GetMapping("/{id}/payment-calendar")
    @Operation(summary = "Календарь выплат MOEX по позициям портфеля")
    public PaymentCalendarResponse paymentCalendar(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer year
    ) {
        return portfolios.paymentCalendar(id, year);
    }

    @PatchMapping("/{id}/settings")
    @Operation(summary = "Настройки портфеля (имя, налог)")
    public PortfolioSettingsResponse updateSettings(
            @PathVariable UUID id,
            @Valid @RequestBody PortfolioSettingsRequest request
    ) {
        return portfolios.updateSettings(id, request);
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

    @PostMapping("/{id}/lazy-seed")
    @Operation(summary = "Ленивый первый ввод позиций (средняя цена и дата)")
    public PortfolioDetailResponse lazySeed(@PathVariable UUID id, @Valid @RequestBody LazyStockSeedRequest request) {
        return portfolios.seedLazy(id, request);
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

    @PostMapping("/{id}/cash/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Внести наличные")
    public HoldingResponse depositCash(@PathVariable UUID id, @Valid @RequestBody CashMovementRequest request) {
        return portfolios.depositCash(id, request);
    }

    @PostMapping("/{id}/cash/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Снять наличные")
    public HoldingResponse withdrawCash(@PathVariable UUID id, @Valid @RequestBody CashMovementRequest request) {
        return portfolios.withdrawCash(id, request);
    }

    @PostMapping("/{id}/cash/dividends")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Дивиденд")
    public HoldingResponse addDividend(@PathVariable UUID id, @Valid @RequestBody CashMovementRequest request) {
        return portfolios.addDividend(id, request);
    }

    @PostMapping("/{id}/cash/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Купон")
    public HoldingResponse addCoupon(@PathVariable UUID id, @Valid @RequestBody CashMovementRequest request) {
        return portfolios.addCoupon(id, request);
    }

    @GetMapping("/{id}/holdings/{holdingId}/transactions")
    @Operation(summary = "История сделок по позиции")
    public List<TradeResponse> transactions(@PathVariable UUID id, @PathVariable UUID holdingId) {
        return portfolios.listTransactions(id, holdingId);
    }

    @GetMapping("/{id}/holdings/{holdingId}/cashflow")
    @Operation(summary = "Параметры денежного потока инструмента")
    public HoldingCashflowResponse getCashflow(@PathVariable UUID id, @PathVariable UUID holdingId) {
        return portfolios.getCashflow(id, holdingId);
    }

    @PatchMapping("/{id}/holdings/{holdingId}/cashflow")
    @Operation(summary = "Обновить денежный поток инструмента")
    public HoldingCashflowResponse updateCashflow(
            @PathVariable UUID id,
            @PathVariable UUID holdingId,
            @Valid @RequestBody HoldingCashflowRequest request
    ) {
        return portfolios.updateCashflow(id, holdingId, request);
    }
}
