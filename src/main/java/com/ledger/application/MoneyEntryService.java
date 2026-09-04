package com.ledger.application;

import com.ledger.api.dto.MoneyEntryRequest;
import com.ledger.api.dto.MoneyEntryResponse;
import com.ledger.domain.MoneyEntry;
import com.ledger.domain.Project;
import com.ledger.infrastructure.persistence.MoneyEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class MoneyEntryService {

    private final MoneyEntryRepository moneyEntries;
    private final ProjectService projects;
    private final String defaultCurrency;

    public MoneyEntryService(
            MoneyEntryRepository moneyEntries,
            ProjectService projects,
            @Value("${ledger.default-currency}") String defaultCurrency
    ) {
        this.moneyEntries = moneyEntries;
        this.projects = projects;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional(readOnly = true)
    public List<MoneyEntryResponse> list(LocalDate from, LocalDate to) {
        return moneyEntries
                .findByOccurredOnGreaterThanEqualAndOccurredOnLessThanEqualOrderByOccurredOnDesc(from, to)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MoneyEntryResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public MoneyEntryResponse create(MoneyEntryRequest request) {
        Project project = projects.requireOptional(request.projectId());
        MoneyEntry entry = new MoneyEntry(
                UUID.randomUUID(),
                project,
                request.direction(),
                request.amount(),
                currency(request),
                request.occurredOn(),
                trimToNull(request.category()),
                trimToNull(request.note())
        );
        return toResponse(moneyEntries.save(entry));
    }

    @Transactional
    public MoneyEntryResponse update(UUID id, MoneyEntryRequest request) {
        MoneyEntry entry = require(id);
        entry.setProject(projects.requireOptional(request.projectId()));
        entry.setDirection(request.direction());
        entry.setAmount(request.amount());
        entry.setCurrency(currency(request));
        entry.setOccurredOn(request.occurredOn());
        entry.setCategory(trimToNull(request.category()));
        entry.setNote(trimToNull(request.note()));
        return toResponse(entry);
    }

    @Transactional
    public void delete(UUID id) {
        moneyEntries.delete(require(id));
    }

    private String currency(MoneyEntryRequest request) {
        if (request.currency() == null || request.currency().isBlank()) {
            return defaultCurrency;
        }
        return request.currency();
    }

    private MoneyEntry require(UUID id) {
        return moneyEntries.findById(id).orElseThrow(() -> new ResourceNotFoundException("MoneyEntry", id));
    }

    private MoneyEntryResponse toResponse(MoneyEntry entry) {
        return new MoneyEntryResponse(
                entry.getId(),
                entry.getProject() == null ? null : entry.getProject().getId(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getOccurredOn(),
                entry.getCategory(),
                entry.getNote(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
