package com.cardplatform.application.transaction.scheduler;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.transaction.model.Transaction;
import com.cardplatform.domain.transaction.model.TransactionHold;
import com.cardplatform.domain.transaction.model.TransactionStatus;
import com.cardplatform.domain.transaction.repository.TransactionHoldRepository;
import com.cardplatform.domain.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldExpirationSchedulerTest {

    @Mock
    private TransactionHoldRepository transactionHoldRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    private HoldExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HoldExpirationScheduler(
                transactionHoldRepository,
                transactionRepository,
                accountRepository
        );
    }

    @Test
    @DisplayName("Should release expired active holds, restore available balances, and update transactions to EXPIRED")
    void shouldProcessExpiredActiveHolds() {
        UUID accountId1 = UUID.randomUUID();
        UUID cardId1 = UUID.randomUUID();
        Instant expiredPast = Instant.now().minusSeconds(3600);

        Transaction tx1 = Transaction.createAuthorization(
                cardId1, accountId1, MonetaryAmount.of(1500.0, "INR"),
                "AUTH-EXP-01", "MERCHANT_EXPIRED", "5411", expiredPast
        );

        TransactionHold hold1 = TransactionHold.create(
                tx1.getId(), accountId1, MonetaryAmount.of(1500.0, "INR"), expiredPast
        );

        Account account1 = Account.create("ACC-EXP-01", "INR", MonetaryAmount.of(10000.0, "INR"));
        account1.placeHold(MonetaryAmount.of(1500.0, "INR"));

        when(transactionHoldRepository.findExpiredActiveHolds(any(Instant.class)))
                .thenReturn(List.of(hold1));
        when(accountRepository.findByIdWithLock(accountId1))
                .thenReturn(Optional.of(account1));
        when(transactionRepository.findById(tx1.getId()))
                .thenReturn(Optional.of(tx1));

        int processed = scheduler.processExpiredHolds();

        assertThat(processed).isEqualTo(1);

        // Verify account hold released and available balance restored
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("10000.0000");
        assertThat(savedAccount.getPendingHoldBalance().getAmount()).isEqualByComparingTo("0.0000");

        // Verify hold is released
        ArgumentCaptor<TransactionHold> holdCaptor = ArgumentCaptor.forClass(TransactionHold.class);
        verify(transactionHoldRepository).save(holdCaptor.capture());
        assertThat(holdCaptor.getValue().isReleased()).isTrue();

        // Verify transaction is EXPIRED
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.EXPIRED);
    }

    @Test
    @DisplayName("Should return 0 and perform no actions when no holds are expired")
    void shouldDoNothingWhenNoExpiredHolds() {
        when(transactionHoldRepository.findExpiredActiveHolds(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        int processed = scheduler.processExpiredHolds();

        assertThat(processed).isEqualTo(0);
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }
}
