package com.ledger.api;

import com.ledger.api.dto.SummaryResponse;
import com.ledger.application.SummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/summary")
@Tag(name = "Summary")
public class SummaryController {

    private final SummaryService summaries;

    public SummaryController(SummaryService summaries) {
        this.summaries = summaries;
    }

    @GetMapping
    @Operation(summary = "Сводка по времени и деньгам за период")
    public SummaryResponse get(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        return summaries.summarize(start, end);
    }
}
