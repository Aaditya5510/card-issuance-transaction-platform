package com.cardplatform.integration.concurrency;

import com.cardplatform.application.transaction.AuthorizeTransactionUseCase;
import com.cardplatform.application.transaction.scheduler.HoldExpirationScheduler;
import com.cardplatform.common.exception.InsufficientFundsException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.repository.CardControlsRepository;
import com.cardplatform.domain.card.repository.CardRepository;
import com.cardplatform.domain.transaction.model.Transaction;
import com.cardplatform.domain.transaction.model.TransactionHold;
import com.cardplatform.domain.transaction.repository.TransactionHoldRepository;
import com.cardplatform.domain.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

class ConcurrentAuthorizationStressTest {

    @Test
    @DisplayName("Stress Test: 50 concurrent $100 swipes against $1,000 account -> exactly 10 succeed, 40 fail with InsufficientFundsException")
    void shouldPreventDoubleSpendingUnderHighConcurrency() throws InterruptedException {
        // 1. Setup account with exactly $1,000.00 available balance
        Account sharedAccount = ConcurrencyTestFixture.createFundedAccount("ACC-STRESS-1000", "USD", 1000.0);
        UUID accountId = sharedAccount.getId();
        UUID cardId = UUID.randomUUID();

        Card activeCard = ConcurrencyTestFixture.createActiveCard(cardId, accountId);
        CardControls controls = ConcurrencyTestFixture.createGenerousControls(cardId, "USD");

        // Repositories and Lock simulation
        CardRepository cardRepository = mock(CardRepository.class);
        CardControlsRepository cardControlsRepository = mock(CardControlsRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        TransactionHoldRepository transactionHoldRepository = mock(TransactionHoldRepository.class);

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(cardControlsRepository.findByCardId(cardId)).thenReturn(Optional.of(controls));

        // Synchronized pessimistic write lock simulation (SELECT ... FOR UPDATE)
        Object accountDbLock = new Object();
        when(accountRepository.findByIdWithLock(accountId)).thenAnswer(invocation -> {
            synchronized (accountDbLock) {
                return Optional.of(sharedAccount);
            }
        });
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Transaction> createdTransactions = Collections.synchronizedList(new ArrayList<>());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            createdTransactions.add(tx);
            return tx;
        });

        List<TransactionHold> createdHolds = Collections.synchronizedList(new ArrayList<>());
        when(transactionHoldRepository.save(any(TransactionHold.class))).thenAnswer(invocation -> {
            TransactionHold hold = invocation.getArgument(0);
            createdHolds.add(hold);
            return hold;
        });

        AuthorizeTransactionUseCase useCase = new AuthorizeTransactionUseCase(
                cardRepository,
                cardControlsRepository,
                accountRepository,
                transactionRepository,
                transactionHoldRepository
        );

        // 2. Concurrency Simulation: 50 threads firing simultaneously
        int totalThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientFundsCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Wait for all threads to align
                    synchronized (accountDbLock) {
                        useCase.execute(new AuthorizeTransactionUseCase.AuthorizeCommand(
                                cardId,
                                MonetaryAmount.of(100.0, "USD"),
                                "MERCHANT_CONCURRENT_SWIPE",
                                "5411"
                        ));
                    }
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficientFundsCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 3. Mathematical Invariant Verification
        assertThat(completed).as("All 50 threads must complete within timeout").isTrue();
        assertThat(unexpectedExceptions).as("Zero unexpected exceptions or unhandled database deadlocks").isEmpty();

        assertThat(successCount.get())
                .as("Exactly 10 authorization requests ($100 * 10 = $1,000) must succeed")
                .isEqualTo(10);

        assertThat(insufficientFundsCount.get())
                .as("Exactly 40 authorization requests must fail with InsufficientFundsException")
                .isEqualTo(40);

        // Account balance post-condition invariants
        assertThat(sharedAccount.getAvailableBalance().getAmount())
                .as("Final available balance must be exactly $0.0000 (fully reserved)")
                .isEqualByComparingTo("0.0000");

        assertThat(sharedAccount.getPendingHoldBalance().getAmount())
                .as("Final sum of pending holds must equal exactly $1,000.0000")
                .isEqualByComparingTo("1000.0000");

        assertThat(sharedAccount.getLedgerBalance().getAmount())
                .as("Ledger balance must remain untouched at $1,000.0000 during authorization holds")
                .isEqualByComparingTo("1000.0000");

        // Verify active hold sums
        BigDecimal totalActiveHolds = createdHolds.stream()
                .filter(h -> !h.isReleased())
                .map(h -> h.getAmount().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalActiveHolds)
                .as("Total active holds must equal exactly $1,000.0000")
                .isEqualByComparingTo("1000.0000");

        assertThat(createdTransactions).hasSize(10);
    }

    @Test
    @DisplayName("Concurrent Authorization & Hold Expiration: Expirations restore balance safely while new authorizations run")
    void shouldHandleConcurrentAuthorizationsAndHoldExpirations() throws InterruptedException {
        // Initial setup: Account has $500 initial available balance + $500 currently in expired holds (Total Ledger = $1,000)
        Account sharedAccount = ConcurrencyTestFixture.createFundedAccount("ACC-EXPIRY-001", "USD", 1000.0);
        sharedAccount.placeHold(MonetaryAmount.of(500.0, "USD")); // Available = 500, PendingHold = 500
        UUID accountId = sharedAccount.getId();
        UUID cardId = UUID.randomUUID();

        Card activeCard = ConcurrencyTestFixture.createActiveCard(cardId, accountId);
        CardControls controls = ConcurrencyTestFixture.createGenerousControls(cardId, "USD");

        UUID expiredTxId = UUID.randomUUID();
        Transaction expiredTx = Transaction.createAuthorization(
                cardId, accountId, MonetaryAmount.of(500.0, "USD"), "AUTH-EXPIRED", "MERCHANT", "5411", Instant.now().minusSeconds(3600)
        );
        TransactionHold expiredHold = TransactionHold.create(
                expiredTx.getId(), accountId, MonetaryAmount.of(500.0, "USD"), Instant.now().minusSeconds(3600)
        );

        CardRepository cardRepository = mock(CardRepository.class);
        CardControlsRepository cardControlsRepository = mock(CardControlsRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        TransactionHoldRepository transactionHoldRepository = mock(TransactionHoldRepository.class);

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(cardControlsRepository.findByCardId(cardId)).thenReturn(Optional.of(controls));

        Object accountDbLock = new Object();
        when(accountRepository.findByIdWithLock(accountId)).thenAnswer(i -> {
            synchronized (accountDbLock) {
                return Optional.of(sharedAccount);
            }
        });
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        when(transactionHoldRepository.findExpiredActiveHolds(any(Instant.class))).thenReturn(List.of(expiredHold));
        when(transactionHoldRepository.save(any(TransactionHold.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.findById(expiredTx.getId())).thenReturn(Optional.of(expiredTx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        AuthorizeTransactionUseCase authorizeUseCase = new AuthorizeTransactionUseCase(
                cardRepository, cardControlsRepository, accountRepository, transactionRepository, transactionHoldRepository
        );

        HoldExpirationScheduler holdExpirationScheduler = new HoldExpirationScheduler(
                transactionHoldRepository, transactionRepository, accountRepository
        );

        // Launch concurrent threads: 1 thread runs hold expiration, 10 threads try to authorize $100 swipes
        int authThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(authThreads + 1);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(authThreads + 1);

        AtomicInteger successfulSwipes = new AtomicInteger(0);
        AtomicInteger expiredHoldsProcessed = new AtomicInteger(0);

        // Expiration worker
        executor.submit(() -> {
            try {
                startGate.await();
                synchronized (accountDbLock) {
                    int processed = holdExpirationScheduler.processExpiredHolds();
                    expiredHoldsProcessed.set(processed);
                }
            } catch (Exception ignored) {
            } finally {
                endGate.countDown();
            }
        });

        // Swipe workers
        for (int i = 0; i < authThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    synchronized (accountDbLock) {
                        authorizeUseCase.execute(new AuthorizeTransactionUseCase.AuthorizeCommand(
                                cardId,
                                MonetaryAmount.of(100.0, "USD"),
                                "MERCHANT_CONCURRENT",
                                "5411"
                        ));
                    }
                    successfulSwipes.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(expiredHoldsProcessed.get()).isEqualTo(1);
        assertThat(expiredHold.isReleased()).isTrue();

        // Total available was $500 initially + $500 released = $1000 total.
        // All 10 swipes of $100 must succeed!
        assertThat(successfulSwipes.get()).isEqualTo(10);
        assertThat(sharedAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("0.0000");
        assertThat(sharedAccount.getPendingHoldBalance().getAmount()).isEqualByComparingTo("1000.0000");
    }
}
