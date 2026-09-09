package com.cardplatform.application.ledger;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.PostingType;

import java.util.List;
import java.util.UUID;

/**
 * Use case interface for recording immutable balanced double-entry Journal Entries.
 */
public interface RecordJournalEntryUseCase {

    JournalEntry execute(RecordJournalEntryCommand command);

    record RecordJournalEntryCommand(
            UUID journalEntryId,
            String idempotencyKey,
            String correlationId,
            String description,
            List<PostingLegCommand> legs
    ) {}

    record PostingLegCommand(
            UUID accountId,
            PostingType type,
            MonetaryAmount amount,
            String description
    ) {}
}
