package com.cardplatform.domain.ledger.repository;

import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.LedgerPosting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository contract for the immutable double-entry ledger engine.
 */
public interface LedgerRepository {

    JournalEntry save(JournalEntry entry);

    default void saveJournalEntry(JournalEntry entry) {
        save(entry);
    }

    Optional<JournalEntry> findById(UUID id);

    Optional<JournalEntry> findByCorrelationId(String correlationId);

    Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey);

    List<LedgerPosting> findPostingsByAccountId(UUID accountId);

    List<LedgerPosting> findPostingsByTransactionRefId(UUID transactionRefId);
}
