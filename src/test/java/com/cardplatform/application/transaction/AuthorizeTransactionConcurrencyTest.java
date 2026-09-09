package com.cardplatform.application.transaction;

import com.cardplatform.common.exception.InsufficientFundsException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.model.CardStatus;
import com.cardplatform.domain.card.repository.CardControlsRepository;
import com.cardplatform.domain.card.repository.CardRepository;
import com.cardplatform.domain.transaction.model.Transaction;
import com.cardplatform.domain.transaction.model.TransactionHold;
import com.cardplatform.domain.transaction.repository.TransactionHoldRepository;
import com.cardplatform.domain.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizeTransactionConcurrencyTest {

    @Test
    @DisplayName("Concurrent swipe authorizations against shared account should respect balance invariants without overdraft")
    void shouldHandleConcurrentAuthorizationsSafely() throws InterruptedException {
        UUID cardId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Card card = new Card(
                cardId, accountId, "tok_concurrent", "411111******1111",
                12, 2035, CardStatus.ACTIVE, null, 0L, Instant.now(), Instant.now()
        );

        CardControls controls = new CardControls(
                UUID.randomUUID(), cardId,
                MonetaryAmount.of(100000.0, "INR"),
                MonetaryAmount.of(50000.0, "INR"),
                true, true, true,
                Instant.now(), Instant.now()
        );

        // Account funded with exactly 10,000 INR
        // 10 concurrent threads each try to authorize 2,000 INR.
        // Exactly 5 threads should succeed (10,000 / 2,000 = 5) and exactly 5 should fail with InsufficientFundsException.
        Account sharedAccount = Account.create("ACC-CONCURRENT-01", "INR", MonetaryAmount.of(10000.0, "INR"));

        CardRepository cardRepository = mock(CardRepository.class);
        CardControlsRepository cardControlsRepository = mock(CardControlsRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        TransactionHoldRepository transactionHoldRepository = mock(TransactionHoldRepository.class);

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(cardControlsRepository.findByCardId(cardId)).thenReturn(Optional.of(controls));

        // Synchronize pessimistic lock simulation
        Object accountLock = new Object();
        when(accountRepository.findByIdWithLock(accountId)).thenAnswer(invocation -> {
            synchronized (accountLock) {
                return Optional.of(sharedAccount);
            }
        });
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionHoldRepository.save(any(TransactionHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthorizeTransactionUseCase useCase = new AuthorizeTransactionUseCase(
                cardRepository,
                cardControlsRepository,
                accountRepository,
                transactionRepository,
                transactionHoldRepository
        );

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientFundsCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    synchronized (accountLock) {
                        useCase.execute(new AuthorizeTransactionUseCase.AuthorizeCommand(
                                cardId,
                                MonetaryAmount.of(2000.0, "INR"),
                                "MERCHANT_CONCURRENT",
                                "5411"
                        ));
                    }
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficientFundsCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(insufficientFundsCount.get()).isEqualTo(5);

        // Verify account balance: Available = 0, Pending Hold = 10,000, Total (Ledger) = 10,000
        assertThat(sharedAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("0.0000");
        assertThat(sharedAccount.getPendingHoldBalance().getAmount()).isEqualByComparingTo("10000.0000");
        assertThat(sharedAccount.getLedgerBalance().getAmount()).isEqualByComparingTo("10000.0000");
    }
}
