package com.cardplatform.application.ledger;

import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.ledger.model.AccountBalances;
import com.cardplatform.domain.ledger.model.EntryType;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.LedgerPosting;
import com.cardplatform.domain.ledger.model.PostingType;
import com.cardplatform.domain.ledger.repository.LedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise Double-Entry Ledger Orchestration Application Service.
 * Provides high-level business entrypoints, balance checks, and compensating reversal handling.
 */
@Service
public class LedgerPostingService {

    private final RecordJournalEntryUseCase recordJournalEntryUseCase;
    private final LedgerRepository ledgerRepository;
    private final AccountRepository accountRepository;

    public LedgerPostingService(
            RecordJournalEntryUseCase recordJournalEntryUseCase,
            LedgerRepository ledgerRepository,
            AccountRepository accountRepository) {
        this.recordJournalEntryUseCase = recordJournalEntryUseCase;
        this.ledgerRepository = ledgerRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public JournalEntry recordTransaction(
            UUID transactionRefId,
            String description,
            List<LedgerPostingCommand> postingCommands) {
        List<RecordJournalEntryUseCase.PostingLegCommand> legs = postingCommands.stream()
                .map(cmd -> new RecordJournalEntryUseCase.PostingLegCommand(
                        cmd.accountId(),
                        cmd.entryType() == EntryType.DEBIT ? PostingType.DEBIT : PostingType.CREDIT,
                        cmd.amount(),
                        cmd.description()
                ))
                .toList();

        RecordJournalEntryUseCase.RecordJournalEntryCommand command =
                new RecordJournalEntryUseCase.RecordJournalEntryCommand(
                        transactionRefId,
                        null,
                        transactionRefId.toString(),
                        description,
                        legs
                );

        return recordJournalEntryUseCase.execute(command);
    }

    @Transactional
    public JournalEntry recordJournalEntry(RecordJournalEntryUseCase.RecordJournalEntryCommand command) {
        return recordJournalEntryUseCase.execute(command);
    }

    /**
     * Creates an immutable compensating reversal entry for an existing journal entry.
     * Reverses all Debits to Credits and all Credits to Debits.
     */
    @Transactional
    public JournalEntry createCompensatingReversal(UUID originalJournalEntryId, String reason) {
        JournalEntry original = ledgerRepository.findById(originalJournalEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Original journal entry not found: " + originalJournalEntryId));

        List<RecordJournalEntryUseCase.PostingLegCommand> reversalLegs = new ArrayList<>();
        for (LedgerPosting leg : original.getPostings()) {
            PostingType oppositeType = leg.getType().opposite();
            reversalLegs.add(new RecordJournalEntryUseCase.PostingLegCommand(
                    leg.getAccountId(),
                    oppositeType,
                    leg.getAmount(),
                    "Reversal: " + (leg.getDescription() != null ? leg.getDescription() : reason)
            ));
        }

        RecordJournalEntryUseCase.RecordJournalEntryCommand reversalCommand =
                new RecordJournalEntryUseCase.RecordJournalEntryCommand(
                        UUID.randomUUID(),
                        null,
                        original.getId().toString(),
                        "Reversal of " + original.getId() + " - " + reason,
                        reversalLegs
                );

        return recordJournalEntryUseCase.execute(reversalCommand);
    }

    @Transactional(readOnly = true)
    public AccountBalances getAccountBalances(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        return AccountBalances.of(account.getId(), account.getAvailableBalance(), account.getPendingHoldBalance());
    }

    @Transactional(readOnly = true)
    public JournalEntry getJournalEntry(UUID id) {
        return ledgerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found: " + id));
    }

    public record LedgerPostingCommand(
            UUID accountId,
            EntryType entryType,
            MonetaryAmount amount,
            String description
    ) {}
}
