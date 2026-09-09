package com.cardplatform.application.ledger;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.ledger.model.AccountBalances;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.PostingType;
import com.cardplatform.domain.ledger.repository.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceTest {

    @Mock
    private RecordJournalEntryUseCase recordJournalEntryUseCase;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private AccountRepository accountRepository;

    private LedgerPostingService service;

    @BeforeEach
    void setUp() {
        service = new LedgerPostingService(
                recordJournalEntryUseCase,
                ledgerRepository,
                accountRepository
        );
    }

    @Test
    @DisplayName("Should successfully generate compensating reversal entry with swapped debits and credits")
    void shouldCreateCompensatingReversal() {
        UUID origId = UUID.randomUUID();
        UUID acc1 = UUID.randomUUID();
        UUID acc2 = UUID.randomUUID();

        JournalEntry original = JournalEntry.builder()
                .id(origId)
                .description("Original Payment")
                .addDebit(acc1, MonetaryAmount.of(150.0, "INR"), "Acc1 Debit")
                .addCredit(acc2, MonetaryAmount.of(150.0, "INR"), "Acc2 Credit")
                .build();

        when(ledgerRepository.findById(origId)).thenReturn(Optional.of(original));
        when(recordJournalEntryUseCase.execute(any())).thenAnswer(i -> {
            RecordJournalEntryUseCase.RecordJournalEntryCommand cmd = i.getArgument(0);
            return JournalEntry.builder()
                    .id(cmd.journalEntryId())
                    .description(cmd.description())
                    .addCredit(acc1, MonetaryAmount.of(150.0, "INR"), "Reversed")
                    .addDebit(acc2, MonetaryAmount.of(150.0, "INR"), "Reversed")
                    .build();
        });

        JournalEntry reversal = service.createCompensatingReversal(origId, "Customer Dispute");

        assertThat(reversal).isNotNull();
        ArgumentCaptor<RecordJournalEntryUseCase.RecordJournalEntryCommand> captor =
                ArgumentCaptor.forClass(RecordJournalEntryUseCase.RecordJournalEntryCommand.class);
        verify(recordJournalEntryUseCase).execute(captor.capture());

        RecordJournalEntryUseCase.RecordJournalEntryCommand recordedCommand = captor.getValue();
        assertThat(recordedCommand.description()).contains("Reversal of " + origId);
        assertThat(recordedCommand.legs()).hasSize(2);

        // Verify that original DEBIT on acc1 became CREDIT
        assertThat(recordedCommand.legs().get(0).accountId()).isEqualTo(acc1);
        assertThat(recordedCommand.legs().get(0).type()).isEqualTo(PostingType.CREDIT);

        // Verify that original CREDIT on acc2 became DEBIT
        assertThat(recordedCommand.legs().get(1).accountId()).isEqualTo(acc2);
        assertThat(recordedCommand.legs().get(1).type()).isEqualTo(PostingType.DEBIT);
    }

    @Test
    @DisplayName("Should query account balances correctly")
    void shouldQueryAccountBalances() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.create("ACC-TEST", "INR", MonetaryAmount.of(2500.0, "INR"));
        account.placeHold(MonetaryAmount.of(500.0, "INR"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        AccountBalances balances = service.getAccountBalances(accountId);

        assertThat(balances.accountId()).isEqualTo(account.getId());
        assertThat(balances.availableBalance().getAmount()).isEqualByComparingTo("2000.0000");
        assertThat(balances.pendingHoldBalance().getAmount()).isEqualByComparingTo("500.0000");
        assertThat(balances.totalBalance().getAmount()).isEqualByComparingTo("2500.0000");
    }
}
