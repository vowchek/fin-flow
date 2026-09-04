package com.ledger.infrastructure.persistence;

import com.ledger.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    List<TimeEntry> findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
            Instant fromInclusive,
            Instant toExclusive
    );
}
