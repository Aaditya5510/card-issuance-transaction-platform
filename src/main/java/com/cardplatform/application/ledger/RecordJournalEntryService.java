package com.cardplatform.application.ledger;

import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.ledger.event.JournalEntryPostedEvent;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.LedgerPosting;
import com.cardplatform.domain.ledger.model.PostingType;
import com.cardplatform.domain.ledger.repository.LedgerRepository;
import com.cardplatform.infrastructure.outbox.OutboxPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core Application Service implementing RecordJournalEntryUseCase.
 * Atomically validates, applies balance adjustments, persists balanced double-entry Journal Entries,
 * and enqueues domain events to the Transactional Outbox.
 */
@Service
public class RecordJournalEntryService implements RecordJournalEntryUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordJournalEntryService.class);

    private final LedgerRepository ledgerRepository;
    private final AccountRepository accountRepository;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;

    public RecordJournalEntryService(
            LedgerRepository ledgerRepository,
            AccountRepository accountRepository,
            OutboxPublisher outboxPublisher,
            ObjectMapper objectMapper) {
        this.ledgerRepository = ledgerRepository;
        this.accountRepository = accountRepository;
        this.outboxPublisher = outboxPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public JournalEntry execute(RecordJournalEntryCommand command) {
        // Idempotency check: If idempotencyKey is supplied and already processed, return existing entry
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<JournalEntry> existing = ledgerRepository.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Returning cached journal entry for idempotency key: {}", command.idempotencyKey());
                return existing.get();
            }
        }

        // Validate existence & active state of all target accounts
        List<LedgerPosting> postings = new ArrayList<>();
        int seq = 1;

        for (PostingLegCommand leg : command.legs()) {
            Account account = accountRepository.findById(leg.accountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found for posting leg: " + leg.accountId()));

            // Apply direct debit or credit to account balance
            if (leg.type() == PostingType.DEBIT) {
                account.directDebit(leg.amount());
            } else {
                account.credit(leg.amount());
            }
            accountRepository.save(account);

            LedgerPosting posting = new LedgerPosting(
                    UUID.randomUUID(),
                    account.getId(),
                    leg.type(),
                    leg.amount(),
                    seq++,
                    leg.description() != null ? leg.description() : command.description(),
                    null,
                    account.getAvailableBalance()
            );
            postings.add(posting);
        }

        // Aggregate validation (Zero-sum invariant checked during JournalEntry instantiation)
        JournalEntry journalEntry = new JournalEntry(
                command.journalEntryId() != null ? command.journalEntryId() : UUID.randomUUID(),
                command.idempotencyKey(),
                command.correlationId(),
                command.description(),
                null,
                postings
        );

        // Persist atomically to immutable double-entry ledger
        JournalEntry savedEntry = ledgerRepository.save(journalEntry);
        log.info("Committed immutable JournalEntry: id={}, legs={}, desc='{}'",
                savedEntry.getId(), savedEntry.getPostings().size(), savedEntry.getDescription());

        // Emit domain event for Transactional Outbox (CDC / Kafka broadcast)
        emitOutboxEvent(savedEntry);

        return savedEntry;
    }

    private void emitOutboxEvent(JournalEntry entry) {
        JournalEntryPostedEvent event = new JournalEntryPostedEvent(
                entry.getId(),
                entry.getIdempotencyKey(),
                entry.getCorrelationId(),
                entry.getDescription(),
                entry.getPostedAt(),
                entry.getPostings()
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            outboxPublisher.publish(
                    "LEDGER_JOURNAL_ENTRY",
                    entry.getId().toString(),
                    "JournalEntryPosted",
                    payloadJson
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize JournalEntryPostedEvent to JSON for outbox", e);
            throw new IllegalStateException("Failed to serialize domain outbox event", e);
        }
    }
}
