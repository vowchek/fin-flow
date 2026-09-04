package com.ledger.api;

import com.ledger.api.dto.MoneyEntryRequest;
import com.ledger.api.dto.MoneyEntryResponse;
import com.ledger.application.MoneyEntryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/money-entries")
@Tag(name = "Money entries")
public class MoneyEntryController {

    private final MoneyEntryService moneyEntries;

    public MoneyEntryController(MoneyEntryService moneyEntries) {
        this.moneyEntries = moneyEntries;
    }

    @GetMapping
    @Operation(summary = "Доходы и расходы за период")
    public List<MoneyEntryResponse> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        return moneyEntries.list(start, end);
    }

    @GetMapping("/{id}")
    public MoneyEntryResponse get(@PathVariable UUID id) {
        return moneyEntries.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MoneyEntryResponse create(@Valid @RequestBody MoneyEntryRequest request) {
        return moneyEntries.create(request);
    }

    @PutMapping("/{id}")
    public MoneyEntryResponse update(@PathVariable UUID id, @Valid @RequestBody MoneyEntryRequest request) {
        return moneyEntries.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        moneyEntries.delete(id);
    }
}
