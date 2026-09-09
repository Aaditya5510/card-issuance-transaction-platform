package com.cardplatform.application.transaction;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.transaction.model.Transaction;
import com.cardplatform.domain.transaction.model.TransactionHold;
import com.cardplatform.domain.transaction.model.TransactionStatus;
import com.cardplatform.domain.transaction.repository.TransactionHoldRepository;
import com.cardplatform.domain.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use Case: Voiding an approved authorization.
 * Immediately releases the active balance hold on the account, restores available balance,
 * and marks the transaction status as VOIDED.
 */
@Service
public class VoidTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(VoidTransactionUseCase.class);

    private final TransactionRepository transactionRepository;
    private final TransactionHoldRepository transactionHoldRepository;
    private final AccountRepository accountRepository;

    public VoidTransactionUseCase(
            TransactionRepository transactionRepository,
            TransactionHoldRepository transactionHoldRepository,
            AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.transactionHoldRepository = transactionHoldRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public VoidResult execute(VoidCommand command) {
        if (command == null || command.transactionId() == null) {
            throw new IllegalArgumentException("VoidCommand with transactionId is required");
        }

        log.info("Processing void for transactionId={}, reason='{}'",
                command.transactionId(), command.reason());

        // 1. Fetch Transaction
        Transaction transaction = transactionRepository.findById(command.transactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + command.transactionId()));

        if (transaction.getStatus() == TransactionStatus.VOIDED) {
            throw new BusinessRuleViolationException(
                    "Transaction is already voided: " + transaction.getId(),
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        if (transaction.getStatus() == TransactionStatus.SETTLED || transaction.getStatus() == TransactionStatus.CAPTURED) {
            throw new BusinessRuleViolationException(
                    "Cannot void settled transaction: " + transaction.getId() + ". Must initiate a refund instead.",
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }

        // 2. Fetch Active Hold
        TransactionHold hold = transactionHoldRepository.findByTransactionId(transaction.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No hold found for transaction: " + transaction.getId()));

        if (hold.isReleased()) {
            throw new BusinessRuleViolationException(
                    "Hold is already released for transaction: " + transaction.getId(),
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }

        // 3. Concurrency Safety: Lock Account
        Account account = accountRepository.findByIdWithLock(transaction.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + transaction.getAccountId()));

        // 4. Release Hold on Account (restores available balance)
        MonetaryAmount releasedAmount = hold.getAmount();
        account.releaseHold(releasedAmount);
        accountRepository.save(account);

        // 5. Mark Hold as released
        hold.release();
        transactionHoldRepository.save(hold);

        // 6. Update Transaction status to VOIDED
        transaction.voidTransaction();
        Transaction savedTx = transactionRepository.save(transaction);

        log.info("Transaction voided successfully: transactionId={}, releasedAmount={}, newStatus={}",
                savedTx.getId(), releasedAmount, savedTx.getStatus());

        return new VoidResult(
                savedTx.getId(),
                savedTx.getStatus(),
                releasedAmount,
                command.reason() != null ? command.reason() : "Customer Void Request"
        );
    }

    public record VoidCommand(
            UUID transactionId,
            String reason
    ) {
        public VoidCommand(UUID transactionId) {
            this(transactionId, "Authorization Voided");
        }
    }

    public record VoidResult(
            UUID transactionId,
            TransactionStatus status,
            MonetaryAmount releasedAmount,
            String reason
    ) {}
}
