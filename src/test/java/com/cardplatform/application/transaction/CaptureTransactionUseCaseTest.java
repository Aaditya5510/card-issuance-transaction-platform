package com.cardplatform.application.transaction;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.transaction.event.TransactionCapturedEvent;
import com.cardplatform.domain.transaction.model.Transaction;
import com.cardplatform.domain.transaction.model.TransactionHold;
import com.cardplatform.domain.transaction.model.TransactionStatus;
import com.cardplatform.domain.transaction.repository.TransactionHoldRepository;
import com.cardplatform.domain.transaction.repository.TransactionRepository;
import com.cardplatform.infrastructure.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
class CaptureTransactionUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionHoldRepository transactionHoldRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OutboxService outboxService;

    private CaptureTransactionUseCase captureTransactionUseCase;

    private UUID transactionId;
    private UUID cardId;
    private UUID accountId;
    private Transaction approvedTransaction;
    private TransactionHold activeHold;
    private Account accountWithHold;

    @BeforeEach
    void setUp() {
        captureTransactionUseCase = new CaptureTransactionUseCase(
                transactionRepository,
                transactionHoldRepository,
                accountRepository,
                null,
                outboxService
        );

        // Account initial balance: 50,000 INR, 5,000 INR in pending hold
        accountWithHold = Account.create("ACC-CAP-001", "INR", MonetaryAmount.of(50000.0, "INR"));
        accountWithHold.placeHold(MonetaryAmount.of(5000.0, "INR"));

        transactionId = UUID.randomUUID();
        cardId = UUID.randomUUID();
        accountId = accountWithHold.getId();

        approvedTransaction = Transaction.createAuthorization(
                cardId,
                accountId,
                MonetaryAmount.of(5000.0, "INR"),
                "AUTH-12345678",
                "MERCHANT_AMAZON",
                "5311",
                Instant.now().plusSeconds(7 * 24 * 3600)
        );

        activeHold = TransactionHold.create(
                approvedTransaction.getId(),
                accountId,
                MonetaryAmount.of(5000.0, "INR"),
                Instant.now().plusSeconds(7 * 24 * 3600)
        );
    }

    @Test
    @DisplayName("Should successfully capture hold, settle balances, mark hold released, update transaction to SETTLED, and emit outbox event")
    void shouldCaptureTransactionSuccessfully() {
        when(transactionRepository.findById(approvedTransaction.getId())).thenReturn(Optional.of(approvedTransaction));
        when(transactionHoldRepository.findByTransactionId(approvedTransaction.getId())).thenReturn(Optional.of(activeHold));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(accountWithHold));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionHoldRepository.save(any(TransactionHold.class))).thenAnswer(i -> i.getArgument(0));

        CaptureTransactionUseCase.CaptureCommand command = new CaptureTransactionUseCase.CaptureCommand(
                approvedTransaction.getId(),
                MonetaryAmount.of(5000.0, "INR")
        );

        CaptureTransactionUseCase.CaptureResult result = captureTransactionUseCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(result.capturedAmount().getAmount()).isEqualByComparingTo("5000.0000");

        // Verify Account balance invariants: pending hold resolved, ledger balance decreased to 45,000
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("45000.0000");
        assertThat(savedAccount.getPendingHoldBalance().getAmount()).isEqualByComparingTo("0.0000");
        assertThat(savedAccount.getLedgerBalance().getAmount()).isEqualByComparingTo("45000.0000");

        // Verify hold is released
        ArgumentCaptor<TransactionHold> holdCaptor = ArgumentCaptor.forClass(TransactionHold.class);
        verify(transactionHoldRepository).save(holdCaptor.capture());
        assertThat(holdCaptor.getValue().isReleased()).isTrue();

        // Verify Transaction is SETTLED
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.SETTLED);

        // Verify Outbox Event emission
        ArgumentCaptor<TransactionCapturedEvent> eventCaptor = ArgumentCaptor.forClass(TransactionCapturedEvent.class);
        verify(outboxService).recordEvent(
                eq("TRANSACTION"),
                eq(approvedTransaction.getId()),
                eq("TransactionCaptured"),
                eventCaptor.capture()
        );
        TransactionCapturedEvent emittedEvent = eventCaptor.getValue();
        assertThat(emittedEvent.transactionId()).isEqualTo(approvedTransaction.getId());
        assertThat(emittedEvent.accountId()).isEqualTo(accountId);
        assertThat(emittedEvent.capturedAmount().getAmount()).isEqualByComparingTo("5000.0000");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when transaction does not exist")
    void shouldThrowWhenTransactionNotFound() {
        UUID missingTxId = UUID.randomUUID();
        when(transactionRepository.findById(missingTxId)).thenReturn(Optional.empty());

        CaptureTransactionUseCase.CaptureCommand command = new CaptureTransactionUseCase.CaptureCommand(missingTxId);

        assertThatThrownBy(() -> captureTransactionUseCase.execute(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    @DisplayName("Should throw BusinessRuleViolationException when transaction is already settled")
    void shouldThrowWhenTransactionAlreadySettled() {
        approvedTransaction.settle();
        when(transactionRepository.findById(approvedTransaction.getId())).thenReturn(Optional.of(approvedTransaction));

        CaptureTransactionUseCase.CaptureCommand command = new CaptureTransactionUseCase.CaptureCommand(approvedTransaction.getId());

        assertThatThrownBy(() -> captureTransactionUseCase.execute(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already settled");

        verify(transactionHoldRepository, never()).findByTransactionId(any());
        verify(outboxService, never()).recordEvent(any(), any(UUID.class), any(), any());
    }

    @Test
    @DisplayName("Should throw BusinessRuleViolationException when hold is already released")
    void shouldThrowWhenHoldAlreadyReleased() {
        activeHold.release();
        when(transactionRepository.findById(approvedTransaction.getId())).thenReturn(Optional.of(approvedTransaction));
        when(transactionHoldRepository.findByTransactionId(approvedTransaction.getId())).thenReturn(Optional.of(activeHold));

        CaptureTransactionUseCase.CaptureCommand command = new CaptureTransactionUseCase.CaptureCommand(approvedTransaction.getId());

        assertThatThrownBy(() -> captureTransactionUseCase.execute(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Hold is already released");

        verify(accountRepository, never()).findByIdWithLock(any());
        verify(outboxService, never()).recordEvent(any(), any(UUID.class), any(), any());
    }

    @Test
    @DisplayName("Should throw BusinessRuleViolationException when capture amount exceeds hold amount")
    void shouldThrowWhenCaptureAmountExceedsHoldAmount() {
        when(transactionRepository.findById(approvedTransaction.getId())).thenReturn(Optional.of(approvedTransaction));
        when(transactionHoldRepository.findByTransactionId(approvedTransaction.getId())).thenReturn(Optional.of(activeHold));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(accountWithHold));

        CaptureTransactionUseCase.CaptureCommand command = new CaptureTransactionUseCase.CaptureCommand(
                approvedTransaction.getId(),
                MonetaryAmount.of(6000.0, "INR") // Hold is only 5,000 INR
        );

        assertThatThrownBy(() -> captureTransactionUseCase.execute(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("exceeds active hold amount");

        verify(outboxService, never()).recordEvent(any(), any(UUID.class), any(), any());
    }
}
