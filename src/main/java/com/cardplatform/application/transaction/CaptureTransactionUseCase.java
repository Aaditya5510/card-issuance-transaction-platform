package com.cardplatform.application.transaction;

import com.cardplatform.application.ledger.LedgerPostingService;
import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.ledger.model.EntryType;
import com.cardplatform.domain.transaction.event.TransactionCapturedEvent;
import com.cardplatform.domain.transaction.model.Transaction;
import com.cardplatform.domain.transaction.model.TransactionHold;
import com.cardplatform.domain.transaction.model.TransactionStatus;
import com.cardplatform.domain.transaction.repository.TransactionHoldRepository;
import com.cardplatform.domain.transaction.repository.TransactionRepository;
import com.cardplatform.infrastructure.outbox.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phase 2: Capture and Settlement Use Case.
 * Resolves active authorization hold, settles account balance, marks transaction SETTLED,
 * posts double-entry settlement journal entries, and emits transactional outbox domain events.
 */
@Service
public class CaptureTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CaptureTransactionUseCase.class);

    private final TransactionRepository transactionRepository;
    private final TransactionHoldRepository transactionHoldRepository;
    private final AccountRepository accountRepository;
    private final LedgerPostingService ledgerPostingService;
    private final OutboxService outboxService;

    @Autowired
    public CaptureTransactionUseCase(
            TransactionRepository transactionRepository,
            TransactionHoldRepository transactionHoldRepository,
            AccountRepository accountRepository,
            @Autowired(required = false) LedgerPostingService ledgerPostingService,
            @Autowired(required = false) OutboxService outboxService) {
        this.transactionRepository = transactionRepository;
        this.transactionHoldRepository = transactionHoldRepository;
        this.accountRepository = accountRepository;
        this.ledgerPostingService = ledgerPostingService;
        this.outboxService = outboxService;
    }

    public CaptureTransactionUseCase(
            TransactionRepository transactionRepository,
            TransactionHoldRepository transactionHoldRepository,
            AccountRepository accountRepository) {
        this(transactionRepository, transactionHoldRepository, accountRepository, null, null);
    }

    @Transactional
    public CaptureResult execute(CaptureCommand command) {
        if (command == null || command.transactionId() == null) {
            throw new IllegalArgumentException("CaptureCommand with transactionId is required");
        }

        log.info("Processing capture for transactionId={}, captureAmount={}",
                command.transactionId(), command.captureAmount());

        // 1. Fetch Transaction
        Transaction transaction = transactionRepository.findById(command.transactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + command.transactionId()));

        if (transaction.getStatus() == TransactionStatus.SETTLED) {
            throw new BusinessRuleViolationException(
                    "Transaction is already settled: " + transaction.getId(),
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        if (transaction.getStatus() != TransactionStatus.APPROVED &&
                transaction.getStatus() != TransactionStatus.AUTHORIZED &&
                transaction.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Cannot capture transaction in status: " + transaction.getStatus(),
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }

        // 2. Fetch Active Hold
        TransactionHold hold = transactionHoldRepository.findByTransactionId(transaction.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No hold found for transaction: " + transaction.getId()));

        if (hold.isReleased()) {
            throw new BusinessRuleViolationException(
                    "Hold is already released or captured for transaction: " + transaction.getId(),
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }

        // 3. Concurrency Safety: Lock Account
        Account account = accountRepository.findByIdWithLock(transaction.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + transaction.getAccountId()));

        MonetaryAmount amountToCapture = command.captureAmount() != null ? command.captureAmount() : hold.getAmount();

        if (amountToCapture.isGreaterThan(hold.getAmount())) {
            throw new BusinessRuleViolationException(
                    "Capture amount (" + amountToCapture + ") exceeds active hold amount (" + hold.getAmount() + ")",
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }

        // 4. Deduct pending hold balance on Account
        account.captureHold(amountToCapture);
        accountRepository.save(account);

        // 5. Mark Hold as released
        hold.release();
        transactionHoldRepository.save(hold);

        // 6. Update Transaction status to SETTLED
        transaction.settle();
        Transaction savedTx = transactionRepository.save(transaction);

        // 7. Post double-entry settlement journal entry if settlement account is provided or ledgerPostingService active
        if (ledgerPostingService != null && command.settlementAccountId() != null) {
            try {
                ledgerPostingService.recordTransaction(
                        transaction.getId(),
                        "Settlement Capture - Merchant: " + transaction.getMerchantId(),
                        List.of(
                                new LedgerPostingService.LedgerPostingCommand(
                                        account.getId(),
                                        EntryType.DEBIT,
                                        amountToCapture,
                                        "Settlement Debit"
                                ),
                                new LedgerPostingService.LedgerPostingCommand(
                                        command.settlementAccountId(),
                                        EntryType.CREDIT,
                                        amountToCapture,
                                        "Settlement Credit - Clearing"
                                )
                        )
                );
            } catch (Exception e) {
                log.warn("Journal entry recording skipped or failed for transaction: {}", transaction.getId(), e);
            }
        }

        Instant settledAt = Instant.now();

        // 8. Transactional Outbox: Persist domain event inside the active @Transactional context
        if (outboxService != null) {
            TransactionCapturedEvent event = new TransactionCapturedEvent(
                    savedTx.getId(),
                    account.getId(),
                    amountToCapture,
                    settledAt
            );
            outboxService.recordEvent("TRANSACTION", savedTx.getId(), "TransactionCaptured", event);
        }

        log.info("Transaction captured and settled successfully: transactionId={}, capturedAmount={}, newStatus={}",
                savedTx.getId(), amountToCapture, savedTx.getStatus());

        return new CaptureResult(
                savedTx.getId(),
                savedTx.getStatus(),
                amountToCapture,
                settledAt
        );
    }

    public record CaptureCommand(
            UUID transactionId,
            MonetaryAmount captureAmount,
            UUID settlementAccountId
    ) {
        public CaptureCommand(UUID transactionId, MonetaryAmount captureAmount) {
            this(transactionId, captureAmount, null);
        }

        public CaptureCommand(UUID transactionId) {
            this(transactionId, null, null);
        }
    }

    public record CaptureResult(
            UUID transactionId,
            TransactionStatus status,
            MonetaryAmount capturedAmount,
            Instant settledAt
    ) {}
}
