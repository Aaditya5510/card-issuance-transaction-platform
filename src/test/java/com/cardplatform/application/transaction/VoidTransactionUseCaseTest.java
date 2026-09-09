package com.cardplatform.application.transaction;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.ResourceNotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoidTransactionUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionHoldRepository transactionHoldRepository;

    @Mock
    private AccountRepository accountRepository;

    private VoidTransactionUseCase voidTransactionUseCase;

    private UUID cardId;
    private UUID accountId;
    private Transaction approvedTransaction;
    private TransactionHold activeHold;
    private Account accountWithHold;

    @BeforeEach
    void setUp() {
        voidTransactionUseCase = new VoidTransactionUseCase(
                transactionRepository,
                transactionHoldRepository,
                accountRepository
        );

        cardId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        approvedTransaction = Transaction.createAuthorization(
                cardId,
                accountId,
                MonetaryAmount.of(4000.0, "INR"),
                "AUTH-VOID-99",
                "MERCHANT_HOTEL",
                "7011",
                Instant.now().plusSeconds(7 * 24 * 3600)
        );

        activeHold = TransactionHold.create(
                approvedTransaction.getId(),
                accountId,
                MonetaryAmount.of(4000.0, "INR"),
                Instant.now().plusSeconds(7 * 24 * 3600)
        );

        // Account initial: 10,000 INR total, 6,000 available + 4,000 hold
        accountWithHold = Account.create("ACC-VOID-01", "INR", MonetaryAmount.of(10000.0, "INR"));
        accountWithHold.placeHold(MonetaryAmount.of(4000.0, "INR"));
    }

    @Test
    @DisplayName("Should successfully void authorization, release hold, restore available balance, and mark transaction VOIDED")
    void shouldVoidTransactionSuccessfully() {
        when(transactionRepository.findById(approvedTransaction.getId())).thenReturn(Optional.of(approvedTransaction));
        when(transactionHoldRepository.findByTransactionId(approvedTransaction.getId())).thenReturn(Optional.of(activeHold));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(accountWithHold));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionHoldRepository.save(any(TransactionHold.class))).thenAnswer(i -> i.getArgument(0));

        VoidTransactionUseCase.VoidCommand command = new VoidTransactionUseCase.VoidCommand(
                approvedTransaction.getId(),
                "Customer cancelled order"
        );

        VoidTransactionUseCase.VoidResult result = voidTransactionUseCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TransactionStatus.VOIDED);
        assertThat(result.releasedAmount().getAmount()).isEqualByComparingTo("4000.0000");
        assertThat(result.reason()).isEqualTo("Customer cancelled order");

        // Verify Account balance invariants: available restored to 10,000, hold 0, ledger balance intact at 10,000
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("10000.0000");
        assertThat(savedAccount.getPendingHoldBalance().getAmount()).isEqualByComparingTo("0.0000");
        assertThat(savedAccount.getLedgerBalance().getAmount()).isEqualByComparingTo("10000.0000");

        // Verify hold is released
        ArgumentCaptor<TransactionHold> holdCaptor = ArgumentCaptor.forClass(TransactionHold.class);
        verify(transactionHoldRepository).save(holdCaptor.capture());
        assertThat(holdCaptor.getValue().isReleased()).isTrue();

        // Verify Transaction is marked VOIDED
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.VOIDED);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when transaction does not exist")
    void shouldThrowWhenTransactionNotFound() {
        UUID unknownTxId = UUID.randomUUID();
        when(transactionRepository.findById(unknownTxId)).thenReturn(Optional.empty());

        VoidTransactionUseCase.VoidCommand command = new VoidTransactionUseCase.VoidCommand(unknownTxId);

        assertThatThrownBy(() -> voidTransactionUseCase.execute(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    @DisplayName("Should throw BusinessRuleViolationException when attempting to void an already settled transaction")
    void shouldThrowWhenTransactionAlreadySettled() {
        approvedTransaction.settle();
        when(transactionRepository.findById(approvedTransaction.getId())).thenReturn(Optional.of(approvedTransaction));

        VoidTransactionUseCase.VoidCommand command = new VoidTransactionUseCase.VoidCommand(approvedTransaction.getId());

        assertThatThrownBy(() -> voidTransactionUseCase.execute(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cannot void settled transaction");

        verify(transactionHoldRepository, never()).findByTransactionId(any());
    }

    @Test
    @DisplayName("Should throw BusinessRuleViolationException when attempting to void an already voided transaction")
    void shouldThrowWhenTransactionAlreadyVoided() {
        approvedTransaction.voidTransaction();
        when(transactionRepository.findById(approvedTransaction.getId())).thenReturn(Optional.of(approvedTransaction));

        VoidTransactionUseCase.VoidCommand command = new VoidTransactionUseCase.VoidCommand(approvedTransaction.getId());

        assertThatThrownBy(() -> voidTransactionUseCase.execute(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already voided");
    }
}
