package com.ledger.application;

import com.ledger.api.dto.TimeEntryRequest;
import com.ledger.api.dto.TimeEntryResponse;
import com.ledger.domain.Project;
import com.ledger.domain.TimeEntry;
import com.ledger.infrastructure.persistence.TimeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class TimeEntryService {

    private final TimeEntryRepository timeEntries;
    private final ProjectService projects;

    public TimeEntryService(TimeEntryRepository timeEntries, ProjectService projects) {
        this.timeEntries = timeEntries;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<TimeEntryResponse> list(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return timeEntries
                .findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(start, end)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TimeEntryResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public TimeEntryResponse create(TimeEntryRequest request) {
        Project project = projects.requireOptional(request.projectId());
        TimeEntry entry = new TimeEntry(
                UUID.randomUUID(),
                project,
                request.startedAt(),
                request.endedAt(),
                trimToNull(request.note())
        );
        return toResponse(timeEntries.save(entry));
    }

    @Transactional
    public TimeEntryResponse update(UUID id, TimeEntryRequest request) {
        TimeEntry entry = require(id);
        entry.setProject(projects.requireOptional(request.projectId()));
        entry.applyRange(request.startedAt(), request.endedAt());
        entry.setNote(trimToNull(request.note()));
        return toResponse(entry);
    }

    @Transactional
    public void delete(UUID id) {
        TimeEntry entry = require(id);
        timeEntries.delete(entry);
    }

    private TimeEntry require(UUID id) {
        return timeEntries.findById(id).orElseThrow(() -> new ResourceNotFoundException("TimeEntry", id));
    }

    private TimeEntryResponse toResponse(TimeEntry entry) {
        return new TimeEntryResponse(
                entry.getId(),
                entry.getProject() == null ? null : entry.getProject().getId(),
                entry.getStartedAt(),
                entry.getEndedAt(),
                entry.getDurationMinutes(),
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
