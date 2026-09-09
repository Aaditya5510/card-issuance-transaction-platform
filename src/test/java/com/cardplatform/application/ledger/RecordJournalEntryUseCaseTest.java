package com.cardplatform.application.ledger;

import com.cardplatform.common.exception.LedgerImbalanceException;
import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.PostingType;
import com.cardplatform.domain.ledger.repository.LedgerRepository;
import com.cardplatform.infrastructure.outbox.OutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordJournalEntryUseCaseTest {

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OutboxPublisher outboxPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private RecordJournalEntryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordJournalEntryService(
                ledgerRepository,
                accountRepository,
                outboxPublisher,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should atomically record balanced journal entry, update account balances, and emit outbox event")
    void shouldRecordBalancedJournalEntrySuccessfully() {
        UUID cashAccId = UUID.randomUUID();
        UUID userAccId = UUID.randomUUID();
        String idempotencyKey = "idemp_rec_1";
        String correlationId = "corr_rec_1";

        Account cashAccount = Account.create("ACC-CASH", "USD", MonetaryAmount.of(5000.0, "USD"));
        Account userAccount = Account.create("ACC-USER", "USD", MonetaryAmount.of(100.0, "USD"));

        when(ledgerRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(accountRepository.findById(cashAccId)).thenReturn(Optional.of(cashAccount));
        when(accountRepository.findById(userAccId)).thenReturn(Optional.of(userAccount));
        when(ledgerRepository.save(any(JournalEntry.class))).thenAnswer(i -> i.getArgument(0));

        RecordJournalEntryUseCase.RecordJournalEntryCommand command =
                new RecordJournalEntryUseCase.RecordJournalEntryCommand(
                        UUID.randomUUID(),
                        idempotencyKey,
                        correlationId,
                        "Top-up Wallet with $200",
                        List.of(
                                new RecordJournalEntryUseCase.PostingLegCommand(cashAccId, PostingType.DEBIT, MonetaryAmount.of(200.0, "USD"), "Cash Inflow"),
                                new RecordJournalEntryUseCase.PostingLegCommand(userAccId, PostingType.CREDIT, MonetaryAmount.of(200.0, "USD"), "User Account Credit")
                        )
                );

        JournalEntry result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.getPostings()).hasSize(2);
        assertThat(result.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(result.getCorrelationId()).isEqualTo(correlationId);

        // Verify account balance mutations
        verify(accountRepository).save(cashAccount);
        verify(accountRepository).save(userAccount);
        assertThat(cashAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("4800.0000");
        assertThat(userAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("300.0000");

        // Verify persistence & Outbox emission
        verify(ledgerRepository).save(any(JournalEntry.class));
        verify(outboxPublisher).publish(
                eq("LEDGER_JOURNAL_ENTRY"),
                eq(result.getId().toString()),
                eq("JournalEntryPosted"),
                any(String.class)
        );
    }

    @Test
    @DisplayName("Should return existing journal entry without reprocessing when idempotency key is matched")
    void shouldReturnCachedEntryOnDuplicateIdempotencyKey() {
        String idempotencyKey = "idemp_duplicate";
        JournalEntry existing = JournalEntry.builder()
                .idempotencyKey(idempotencyKey)
                .description("Already processed")
                .addDebit(UUID.randomUUID(), MonetaryAmount.of(50.0, "USD"), "D")
                .addCredit(UUID.randomUUID(), MonetaryAmount.of(50.0, "USD"), "C")
                .build();

        when(ledgerRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        RecordJournalEntryUseCase.RecordJournalEntryCommand command =
                new RecordJournalEntryUseCase.RecordJournalEntryCommand(
                        UUID.randomUUID(),
                        idempotencyKey,
                        "corr_dup",
                        "Duplicate attempt",
                        List.of()
                );

        JournalEntry result = useCase.execute(command);

        assertThat(result).isSameAs(existing);
        verify(ledgerRepository, never()).save(any());
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when account for a leg is not found")
    void shouldThrowWhenAccountNotFound() {
        UUID missingAccId = UUID.randomUUID();
        when(accountRepository.findById(missingAccId)).thenReturn(Optional.empty());

        RecordJournalEntryUseCase.RecordJournalEntryCommand command =
                new RecordJournalEntryUseCase.RecordJournalEntryCommand(
                        UUID.randomUUID(),
                        null,
                        "corr_missing",
                        "Missing account test",
                        List.of(
                                new RecordJournalEntryUseCase.PostingLegCommand(missingAccId, PostingType.DEBIT, MonetaryAmount.of(100.0, "USD"), "D")
                        )
                );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found for posting leg");
    }

    @Test
    @DisplayName("Should throw LedgerImbalanceException and abort transaction when postings are unbalanced")
    void shouldAbortWhenPostingsAreUnbalanced() {
        UUID acc1 = UUID.randomUUID();
        UUID acc2 = UUID.randomUUID();

        Account a1 = Account.create("A1", "USD", MonetaryAmount.of(1000.0, "USD"));
        Account a2 = Account.create("A2", "USD", MonetaryAmount.of(1000.0, "USD"));

        when(accountRepository.findById(acc1)).thenReturn(Optional.of(a1));
        when(accountRepository.findById(acc2)).thenReturn(Optional.of(a2));

        RecordJournalEntryUseCase.RecordJournalEntryCommand command =
                new RecordJournalEntryUseCase.RecordJournalEntryCommand(
                        UUID.randomUUID(),
                        null,
                        "corr_unbal",
                        "Unbalanced attempt",
                        List.of(
                                new RecordJournalEntryUseCase.PostingLegCommand(acc1, PostingType.DEBIT, MonetaryAmount.of(100.0, "USD"), "D 100"),
                                new RecordJournalEntryUseCase.PostingLegCommand(acc2, PostingType.CREDIT, MonetaryAmount.of(80.0, "USD"), "C 80")
                        )
                );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(LedgerImbalanceException.class)
                .hasMessageContaining("Mathematical Zero-Sum Invariant Failed");

        verify(ledgerRepository, never()).save(any());
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }
}
