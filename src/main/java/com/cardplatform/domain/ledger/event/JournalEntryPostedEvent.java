package com.cardplatform.domain.ledger.event;

import com.cardplatform.domain.ledger.model.LedgerPosting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event emitted whenever an immutable JournalEntry is successfully committed.
 */
public record JournalEntryPostedEvent(
        UUID journalEntryId,
        String idempotencyKey,
        String correlationId,
        String description,
        Instant postedAt,
        List<LedgerPosting> postings
) {
    public JournalEntryPostedEvent {
        postings = List.copyOf(postings);
    }
}
