package com.cardplatform.application.transaction;

import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.ledger.event.JournalEntryPostedEvent;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.LedgerPosting;
import com.cardplatform.domain.ledger.model.PostingType;
import com.cardplatform.domain.ledger.repository.LedgerRepository;
import com.cardplatform.domain.transaction.model.AuthorizationHold;
import com.cardplatform.domain.transaction.repository.AuthorizationRepository;
import com.cardplatform.infrastructure.outbox.OutboxPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Phase 2: Settlement and Clearing Use Case.
 * Releases/captures authorization hold, credits merchant clearing account, and posts final debits/credits to the immutable ledger.
 */
@Service
public class SettlementUseCase {

    private static final Logger log = LoggerFactory.getLogger(SettlementUseCase.class);

    private final AuthorizationRepository authorizationRepository;
    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;

    public SettlementUseCase(
            AuthorizationRepository authorizationRepository,
            AccountRepository accountRepository,
            LedgerRepository ledgerRepository,
            OutboxPublisher outboxPublisher,
            ObjectMapper objectMapper) {
        this.authorizationRepository = authorizationRepository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.outboxPublisher = outboxPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JournalEntry settleAuthorization(UUID authorizationId, UUID settlementAccountId) {
        AuthorizationHold hold = authorizationRepository.findById(authorizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Authorization hold not found: " + authorizationId));

        Account cardholderAccount = accountRepository.findById(hold.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Cardholder account not found: " + hold.getAccountId()));

        Account merchantAccount = accountRepository.findById(settlementAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant clearing account not found: " + settlementAccountId));

        // 1. Capture hold on cardholder account (releases pending hold balance, available balance was reserved in auth)
        cardholderAccount.captureHold(hold.getAmount());
        accountRepository.save(cardholderAccount);

        // 2. Credit merchant settlement clearing account
        merchantAccount.credit(hold.getAmount());
        accountRepository.save(merchantAccount);

        // 3. Mark hold as captured
        hold.capture();
        authorizationRepository.save(hold);

        // 4. Record balanced double-entry in the ledger: Cardholder Debit, Settlement Account Credit
        LedgerPosting cardholderPosting = new LedgerPosting(
                UUID.randomUUID(),
                cardholderAccount.getId(),
                PostingType.DEBIT,
                hold.getAmount(),
                1,
                "Settlement Capture - " + hold.getMerchantName(),
                null,
                cardholderAccount.getAvailableBalance()
        );

        LedgerPosting merchantPosting = new LedgerPosting(
                UUID.randomUUID(),
                merchantAccount.getId(),
                PostingType.CREDIT,
                hold.getAmount(),
                2,
                "Settlement Credit - Merchant Payout: " + hold.getMerchantName(),
                null,
                merchantAccount.getAvailableBalance()
        );

        JournalEntry journalEntry = new JournalEntry(
                UUID.randomUUID(),
                null,
                hold.getId().toString(),
                "Settlement Clearing: " + hold.getMerchantName(),
                null,
                List.of(cardholderPosting, merchantPosting)
        );

        JournalEntry savedEntry = ledgerRepository.save(journalEntry);
        log.info("Committed settlement JournalEntry: id={}, txRef={}, desc='{}'",
                savedEntry.getId(), savedEntry.getTransactionRefId(), savedEntry.getDescription());

        // 5. Emit transactional outbox event for ledger posting
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
