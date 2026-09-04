package com.ledger.application;

import com.ledger.api.dto.SummaryResponse;
import com.ledger.domain.MoneyDirection;
import com.ledger.domain.MoneyEntry;
import com.ledger.domain.TimeEntry;
import com.ledger.infrastructure.persistence.MoneyEntryRepository;
import com.ledger.infrastructure.persistence.TimeEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class SummaryService {

    private final TimeEntryRepository timeEntries;
    private final MoneyEntryRepository moneyEntries;
    private final String defaultCurrency;

    public SummaryService(
            TimeEntryRepository timeEntries,
            MoneyEntryRepository moneyEntries,
            @Value("${ledger.default-currency}") String defaultCurrency
    ) {
        this.timeEntries = timeEntries;
        this.moneyEntries = moneyEntries;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summarize(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<TimeEntry> times = timeEntries
                .findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(start, end);
        List<MoneyEntry> money = moneyEntries
                .findByOccurredOnGreaterThanEqualAndOccurredOnLessThanEqualOrderByOccurredOnDesc(from, to);

        long totalMinutes = times.stream().mapToLong(TimeEntry::getDurationMinutes).sum();
        BigDecimal income = sum(money, MoneyDirection.INCOME);
        BigDecimal expense = sum(money, MoneyDirection.EXPENSE);

        return new SummaryResponse(
                times.size(),
                totalMinutes,
                money.size(),
                income,
                expense,
                income.subtract(expense),
                defaultCurrency
        );
    }

    private static BigDecimal sum(List<MoneyEntry> money, MoneyDirection direction) {
        return money.stream()
                .filter(entry -> entry.getDirection() == direction)
                .map(MoneyEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
