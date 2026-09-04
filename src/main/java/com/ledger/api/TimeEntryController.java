package com.ledger.api;

import com.ledger.api.dto.TimeEntryRequest;
import com.ledger.api.dto.TimeEntryResponse;
import com.ledger.application.TimeEntryService;
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
@RequestMapping("/api/v1/time-entries")
@Tag(name = "Time entries")
public class TimeEntryController {

    private final TimeEntryService timeEntries;

    public TimeEntryController(TimeEntryService timeEntries) {
        this.timeEntries = timeEntries;
    }

    @GetMapping
    @Operation(summary = "Записи времени за период")
    public List<TimeEntryResponse> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        return timeEntries.list(start, end);
    }

    @GetMapping("/{id}")
    public TimeEntryResponse get(@PathVariable UUID id) {
        return timeEntries.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimeEntryResponse create(@Valid @RequestBody TimeEntryRequest request) {
        return timeEntries.create(request);
    }

    @PutMapping("/{id}")
    public TimeEntryResponse update(@PathVariable UUID id, @Valid @RequestBody TimeEntryRequest request) {
        return timeEntries.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        timeEntries.delete(id);
    }
}
