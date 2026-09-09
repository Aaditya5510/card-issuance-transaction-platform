package com.cardplatform.application.transaction;

import com.cardplatform.application.ledger.LedgerPostingService;
import com.cardplatform.common.exception.CardExpiredException;
import com.cardplatform.common.exception.CardFrozenException;
import com.cardplatform.common.exception.InsufficientFundsException;
import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.model.CardStatus;
import com.cardplatform.domain.card.repository.CardControlsRepository;
import com.cardplatform.domain.card.repository.CardRepository;
import com.cardplatform.domain.transaction.event.TransactionAuthorizedEvent;
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
import java.util.UUID;

/**
 * Phase 1: High-Throughput Two-Phase Card Authorization Use Case.
 * Concurrency Safe: Acquires pessimistic write lock on Account to guarantee balance invariants.
 * Strictly verifies card controls, daily/single swipe limits, and active balance hold reservation.
 * Emits transactional outbox domain events inside the active @Transactional boundary.
 */
@Service
public class AuthorizeTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthorizeTransactionUseCase.class);

    private final CardRepository cardRepository;
    private final CardControlsRepository cardControlsRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionHoldRepository transactionHoldRepository;
    private final LedgerPostingService ledgerPostingService;
    private final OutboxService outboxService;

    @Autowired
    public AuthorizeTransactionUseCase(
            CardRepository cardRepository,
            CardControlsRepository cardControlsRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            TransactionHoldRepository transactionHoldRepository,
            @Autowired(required = false) LedgerPostingService ledgerPostingService,
            @Autowired(required = false) OutboxService outboxService) {
        this.cardRepository = cardRepository;
        this.cardControlsRepository = cardControlsRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionHoldRepository = transactionHoldRepository;
        this.ledgerPostingService = ledgerPostingService;
        this.outboxService = outboxService;
    }

    public AuthorizeTransactionUseCase(
            CardRepository cardRepository,
            CardControlsRepository cardControlsRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            TransactionHoldRepository transactionHoldRepository) {
        this(cardRepository, cardControlsRepository, accountRepository, transactionRepository, transactionHoldRepository, null, null);
    }

    @Transactional
    public AuthorizationResult execute(AuthorizeCommand command) {
        if (command == null || command.cardId() == null || command.amount() == null) {
            throw new IllegalArgumentException("AuthorizeCommand with cardId and positive amount is required");
        }

        log.info("Processing authorization request: cardId={}, amount={}, merchantId={}, mcc={}",
                command.cardId(), command.amount(), command.merchantId(), command.mcc());

        // 1. Fetch Card
        Card card = cardRepository.findById(command.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with ID: " + command.cardId()));

        // 2. Strict Card Status & Control Checks
        if (card.getStatus() == CardStatus.FROZEN) {
            throw new CardFrozenException("Card is frozen: " + card.getId());
        }
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new CardFrozenException("Card is not active (status: " + card.getStatus() + "): " + card.getId());
        }
        if (card.isExpired()) {
            throw new CardExpiredException("Card has expired (expiry: " + card.getExpiryMonth() + "/" + card.getExpiryYear() + ")");
        }

        CardControls controls = cardControlsRepository.findByCardId(card.getId()).orElse(null);
        card.attachControls(controls);
        card.validateForTransaction(command.amount(), true, false, false);

        // 3. Concurrency Safety: Lock Account with PESSIMISTIC_WRITE (SELECT ... FOR UPDATE)
        Account account = accountRepository.findByIdWithLock(card.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for card: " + card.getId()));

        // 4. Validate Available Balance
        if (account.getAvailableBalance().isLessThan(command.amount())) {
            throw new InsufficientFundsException(
                    "Insufficient available balance for authorization hold. Available: " +
                            account.getAvailableBalance() + ", Required: " + command.amount()
            );
        }

        // 5. Generate Authorization Code
        String authCode = "AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 6. Deduct available balance and place hold
        account.placeHold(command.amount());
        accountRepository.save(account);

        // 7. Create APPROVED Transaction & Active Hold
        Instant expiresAt = Instant.now().plusSeconds(7 * 24 * 3600); // 7-day standard hold window
        Transaction transaction = Transaction.createAuthorization(
                card.getId(),
                account.getId(),
                command.amount(),
                authCode,
                command.merchantId() != null ? command.merchantId() : "MERCHANT-UNKNOWN",
                command.mcc() != null ? command.mcc() : "0000",
                expiresAt
        );
        Transaction savedTx = transactionRepository.save(transaction);

        TransactionHold hold = TransactionHold.create(savedTx.getId(), account.getId(), command.amount(), expiresAt);
        transactionHoldRepository.save(hold);

        // 8. Transactional Outbox: Persist domain event inside the active @Transactional context
        if (outboxService != null) {
            TransactionAuthorizedEvent event = new TransactionAuthorizedEvent(
                    savedTx.getId(),
                    card.getId(),
                    account.getId(),
                    savedTx.getAmount(),
                    authCode,
                    savedTx.getMerchantId(),
                    savedTx.getMerchantCategoryCode(),
                    Instant.now(),
                    expiresAt
            );
            outboxService.recordEvent("TRANSACTION", savedTx.getId(), "TransactionAuthorized", event);
        }

        log.info("Authorization approved: transactionId={}, authCode={}, holdId={}, accountId={}",
                savedTx.getId(), authCode, hold.getId(), account.getId());

        return new AuthorizationResult(
                savedTx.getId(),
                authCode,
                savedTx.getStatus(),
                savedTx.getAmount(),
                expiresAt,
                "00",
                "APPROVED"
        );
    }

    public record AuthorizeCommand(
            UUID cardId,
            MonetaryAmount amount,
            String merchantId,
            String mcc
    ) {
        public String merchantCategoryCode() {
            return mcc;
        }
    }

    public record AuthorizationResult(
            UUID transactionId,
            String authorizationCode,
            TransactionStatus status,
            MonetaryAmount amount,
            Instant expiresAt,
            String responseCode,
            String message
    ) {}
}
