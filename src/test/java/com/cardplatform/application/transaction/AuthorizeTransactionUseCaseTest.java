package com.cardplatform.application.transaction;

import com.cardplatform.common.exception.CardExpiredException;
import com.cardplatform.common.exception.CardFrozenException;
import com.cardplatform.common.exception.CardLimitExceededException;
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
class AuthorizeTransactionUseCaseTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardControlsRepository cardControlsRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionHoldRepository transactionHoldRepository;

    @Mock
    private OutboxService outboxService;

    private AuthorizeTransactionUseCase authorizeTransactionUseCase;

    private UUID cardId;
    private UUID accountId;
    private Card activeCard;
    private CardControls defaultControls;
    private Account fundedAccount;

    @BeforeEach
    void setUp() {
        authorizeTransactionUseCase = new AuthorizeTransactionUseCase(
                cardRepository,
                cardControlsRepository,
                accountRepository,
                transactionRepository,
                transactionHoldRepository,
                null,
                outboxService
        );

        fundedAccount = Account.create("ACC-SWIPE-101", "INR", MonetaryAmount.of(20000.0, "INR"));
        cardId = UUID.randomUUID();
        accountId = fundedAccount.getId();

        activeCard = new Card(
                cardId, accountId, "tok_swipe123", "411111******1111",
                12, 2035, CardStatus.ACTIVE, null, 0L, Instant.now(), Instant.now()
        );

        defaultControls = new CardControls(
                UUID.randomUUID(), cardId,
                MonetaryAmount.of(50000.0, "INR"),
                MonetaryAmount.of(10000.0, "INR"),
                true, true, true,
                Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("Should successfully authorize transaction, place hold, and emit TransactionAuthorized outbox event")
    void shouldAuthorizeTransactionSuccessfully() {
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(cardControlsRepository.findByCardId(cardId)).thenReturn(Optional.of(defaultControls));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(fundedAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionHoldRepository.save(any(TransactionHold.class))).thenAnswer(i -> i.getArgument(0));

        AuthorizeTransactionUseCase.AuthorizeCommand command = new AuthorizeTransactionUseCase.AuthorizeCommand(
                cardId,
                MonetaryAmount.of(3500.0, "INR"),
                "MERCHANT_APPLE_STORE",
                "5732"
        );

        AuthorizeTransactionUseCase.AuthorizationResult result = authorizeTransactionUseCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(result.authorizationCode()).startsWith("AUTH-");
        assertThat(result.amount().getAmount()).isEqualByComparingTo("3500.0000");

        // Verify account balance invariants: available deducted, pending hold increased, ledger balance unchanged
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("16500.0000");
        assertThat(savedAccount.getPendingHoldBalance().getAmount()).isEqualByComparingTo("3500.0000");
        assertThat(savedAccount.getLedgerBalance().getAmount()).isEqualByComparingTo("20000.0000");

        // Verify Transaction & Hold persistence
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction savedTx = txCaptor.getValue();
        assertThat(savedTx.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(savedTx.getMerchantId()).isEqualTo("MERCHANT_APPLE_STORE");
        assertThat(savedTx.getMerchantCategoryCode()).isEqualTo("5732");

        ArgumentCaptor<TransactionHold> holdCaptor = ArgumentCaptor.forClass(TransactionHold.class);
        verify(transactionHoldRepository).save(holdCaptor.capture());
        TransactionHold savedHold = holdCaptor.getValue();
        assertThat(savedHold.isReleased()).isFalse();
        assertThat(savedHold.getAmount().getAmount()).isEqualByComparingTo("3500.0000");

        // Verify Transactional Outbox emission
        ArgumentCaptor<TransactionAuthorizedEvent> eventCaptor = ArgumentCaptor.forClass(TransactionAuthorizedEvent.class);
        verify(outboxService).recordEvent(
                eq("TRANSACTION"),
                eq(savedTx.getId()),
                eq("TransactionAuthorized"),
                eventCaptor.capture()
        );
        TransactionAuthorizedEvent emittedEvent = eventCaptor.getValue();
        assertThat(emittedEvent.transactionId()).isEqualTo(savedTx.getId());
        assertThat(emittedEvent.cardId()).isEqualTo(cardId);
        assertThat(emittedEvent.accountId()).isEqualTo(accountId);
        assertThat(emittedEvent.amount().getAmount()).isEqualByComparingTo("3500.0000");
    }

    @Test
    @DisplayName("Should decline authorization with CardFrozenException when card is frozen")
    void shouldDeclineWhenCardIsFrozen() {
        Card frozenCard = new Card(
                cardId, accountId, "tok_frozen", "411111******1111",
                12, 2035, CardStatus.FROZEN, null, 0L, Instant.now(), Instant.now()
        );
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(frozenCard));

        AuthorizeTransactionUseCase.AuthorizeCommand command = new AuthorizeTransactionUseCase.AuthorizeCommand(
                cardId,
                MonetaryAmount.of(500.0, "INR"),
                "MERCHANT_GROCERY",
                "5411"
        );

        assertThatThrownBy(() -> authorizeTransactionUseCase.execute(command))
                .isInstanceOf(CardFrozenException.class)
                .hasMessageContaining("Card is frozen");

        verify(accountRepository, never()).findByIdWithLock(any());
        verify(transactionRepository, never()).save(any());
        verify(outboxService, never()).recordEvent(any(), any(UUID.class), any(), any());
    }

    @Test
    @DisplayName("Should decline authorization with InsufficientFundsException when account balance is insufficient")
    void shouldDeclineWhenInsufficientFunds() {
        Account lowBalanceAccount = Account.create("ACC-LOW-01", "INR", MonetaryAmount.of(100.0, "INR"));

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(cardControlsRepository.findByCardId(cardId)).thenReturn(Optional.of(defaultControls));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(lowBalanceAccount));

        AuthorizeTransactionUseCase.AuthorizeCommand command = new AuthorizeTransactionUseCase.AuthorizeCommand(
                cardId,
                MonetaryAmount.of(5000.0, "INR"),
                "MERCHANT_FLIGHTS",
                "4511"
        );

        assertThatThrownBy(() -> authorizeTransactionUseCase.execute(command))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient available balance");

        verify(transactionRepository, never()).save(any());
        verify(transactionHoldRepository, never()).save(any());
        verify(outboxService, never()).recordEvent(any(), any(UUID.class), any(), any());
    }

    @Test
    @DisplayName("Should decline authorization with CardLimitExceededException when single transaction limit is exceeded")
    void shouldDeclineWhenSingleTransactionLimitExceeded() {
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(cardControlsRepository.findByCardId(cardId)).thenReturn(Optional.of(defaultControls));

        // Default perTxLimit is 10,000 INR
        AuthorizeTransactionUseCase.AuthorizeCommand command = new AuthorizeTransactionUseCase.AuthorizeCommand(
                cardId,
                MonetaryAmount.of(15000.0, "INR"),
                "MERCHANT_JEWELRY",
                "5944"
        );

        assertThatThrownBy(() -> authorizeTransactionUseCase.execute(command))
                .isInstanceOf(CardLimitExceededException.class)
                .hasMessageContaining("exceeds single transaction limit");

        verify(accountRepository, never()).findByIdWithLock(any());
        verify(transactionRepository, never()).save(any());
        verify(outboxService, never()).recordEvent(any(), any(UUID.class), any(), any());
    }

    @Test
    @DisplayName("Should decline authorization with CardLimitExceededException when daily limit is exceeded")
    void shouldDeclineWhenDailyLimitExceeded() {
        CardControls restrictiveDailyLimit = new CardControls(
                UUID.randomUUID(), cardId,
                MonetaryAmount.of(2000.0, "INR"),
                MonetaryAmount.of(5000.0, "INR"),
                true, true, true,
                Instant.now(), Instant.now()
        );

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(activeCard));
        when(cardControlsRepository.findByCardId(cardId)).thenReturn(Optional.of(restrictiveDailyLimit));

        AuthorizeTransactionUseCase.AuthorizeCommand command = new AuthorizeTransactionUseCase.AuthorizeCommand(
                cardId,
                MonetaryAmount.of(3000.0, "INR"),
                "MERCHANT_ELECTRONICS",
                "5732"
        );

        assertThatThrownBy(() -> authorizeTransactionUseCase.execute(command))
                .isInstanceOf(CardLimitExceededException.class)
                .hasMessageContaining("exceeds daily limit");
    }

    @Test
    @DisplayName("Should decline authorization with CardExpiredException when card has expired")
    void shouldDeclineWhenCardHasExpired() {
        Card expiredCard = new Card(
                cardId, accountId, "tok_expired", "411111******1111",
                1, 2020, CardStatus.ACTIVE, null, 0L, Instant.now(), Instant.now()
        );
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(expiredCard));

        AuthorizeTransactionUseCase.AuthorizeCommand command = new AuthorizeTransactionUseCase.AuthorizeCommand(
                cardId,
                MonetaryAmount.of(500.0, "INR"),
                "MERCHANT_CAFE",
                "5812"
        );

        assertThatThrownBy(() -> authorizeTransactionUseCase.execute(command))
                .isInstanceOf(CardExpiredException.class)
                .hasMessageContaining("Card has expired");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when card is not found")
    void shouldThrowWhenCardNotFound() {
        UUID unknownCardId = UUID.randomUUID();
        when(cardRepository.findById(unknownCardId)).thenReturn(Optional.empty());

        AuthorizeTransactionUseCase.AuthorizeCommand command = new AuthorizeTransactionUseCase.AuthorizeCommand(
                unknownCardId,
                MonetaryAmount.of(500.0, "INR"),
                "MERCHANT_TEST",
                "5411"
        );

        assertThatThrownBy(() -> authorizeTransactionUseCase.execute(command))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Card not found");
    }
}
