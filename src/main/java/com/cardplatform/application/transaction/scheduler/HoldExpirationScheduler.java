package com.cardplatform.application.transaction.scheduler;

import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.transaction.model.Transaction;
import com.cardplatform.domain.transaction.model.TransactionHold;
import com.cardplatform.domain.transaction.repository.TransactionHoldRepository;
import com.cardplatform.domain.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Background Scheduler: Identifies stale/expired authorization holds and releases funds.
 * Periodically queries active holds past their expiration timestamp, restores account available balance,
 * and updates the corresponding transaction state to EXPIRED.
 */
@Component
@EnableScheduling
public class HoldExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpirationScheduler.class);

    private final TransactionHoldRepository transactionHoldRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public HoldExpirationScheduler(
            TransactionHoldRepository transactionHoldRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository) {
        this.transactionHoldRepository = transactionHoldRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Runs periodically to expire stale active holds.
     * Fixed delay is configurable via app properties, defaulting to 60 seconds.
     */
    @Scheduled(fixedDelayString = "${app.scheduler.hold-expiration.fixed-delay:60000}", initialDelay = 10000)
    public int processExpiredHolds() {
        Instant now = Instant.now();
        List<TransactionHold> expiredHolds = transactionHoldRepository.findExpiredActiveHolds(now);

        if (expiredHolds.isEmpty()) {
            return 0;
        }

        log.info("Found {} expired active hold(s) to release at timestamp {}", expiredHolds.size(), now);
        int processedCount = 0;

        for (TransactionHold hold : expiredHolds) {
            try {
                releaseExpiredHold(hold);
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to release expired hold: holdId={}, transactionId={}",
                        hold.getId(), hold.getTransactionId(), e);
            }
        }

        log.info("Successfully processed {} expired hold(s)", processedCount);
        return processedCount;
    }

    @Transactional
    public void releaseExpiredHold(TransactionHold hold) {
        // 1. Lock Account and release hold to restore available balance
        Optional<Account> accountOpt = accountRepository.findByIdWithLock(hold.getAccountId());
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            account.releaseHold(hold.getAmount());
            accountRepository.save(account);
        } else {
            log.warn("Account not found for expired hold: accountId={}", hold.getAccountId());
        }

        // 2. Mark Hold as released
        hold.release();
        transactionHoldRepository.save(hold);

        // 3. Mark corresponding Transaction as EXPIRED
        Optional<Transaction> txOpt = transactionRepository.findById(hold.getTransactionId());
        if (txOpt.isPresent()) {
            Transaction transaction = txOpt.get();
            try {
                transaction.expire();
                transactionRepository.save(transaction);
            } catch (Exception e) {
                log.warn("Transaction status transition to EXPIRED skipped: txId={}, currentStatus={}",
                        transaction.getId(), transaction.getStatus());
            }
        }

        log.info("Released expired hold: holdId={}, amount={}, accountId={}, txId={}",
                hold.getId(), hold.getAmount(), hold.getAccountId(), hold.getTransactionId());
    }
}
