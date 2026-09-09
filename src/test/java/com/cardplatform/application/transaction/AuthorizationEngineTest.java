package com.cardplatform.application.transaction;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.model.AccountStatus;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.model.CardStatus;
import com.cardplatform.domain.card.repository.CardControlsRepository;
import com.cardplatform.domain.card.repository.CardRepository;
import com.cardplatform.domain.transaction.model.AuthorizationHold;
import com.cardplatform.domain.transaction.model.AuthorizationRequest;
import com.cardplatform.domain.transaction.model.TransactionStatus;
import com.cardplatform.domain.transaction.repository.AuthorizationRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationEngineTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardControlsRepository cardControlsRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AuthorizationRepository authorizationRepository;

    private AuthorizationEngine authorizationEngine;

    @BeforeEach
    void setUp() {
        authorizationEngine = new AuthorizationEngine(
                cardRepository,
                cardControlsRepository,
                accountRepository,
                authorizationRepository
        );
    }

    @Test
    @DisplayName("Should successfully process authorization hold when limits and balance are valid")
    void shouldAuthorizeTransactionSuccessfully() {
        UUID accountId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "key_tx_12345";

        Card card = new Card(
                cardId, accountId, "tok_abc123", "411111******1111",
                12, 2030, CardStatus.ACTIVE, null, 0L, Instant.now(), Instant.now()
        );
        CardControls controls = CardControls.defaultControls(cardId, "INR");
        Account account = Account.create("ACC-987654", "INR", MonetaryAmount.of(10000.0, "INR"));

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(cardControlsRepository.findByCardId(cardId)).thenReturn(Optional.of(controls));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));
        when(authorizationRepository.save(any(AuthorizationHold.class))).thenAnswer(i -> i.getArgument(0));

        AuthorizationRequest request = new AuthorizationRequest(
                idempotencyKey, cardId, null, MonetaryAmount.of(2500.0, "INR"),
                "Amazon Pay", true, false, false
        );

        AuthorizationHold result = authorizationEngine.processAuthorization(request);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.AUTHORIZED);
        assertThat(result.getAmount().getAmount()).isEqualByComparingTo("2500.0000");

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getAvailableBalance().getAmount()).isEqualByComparingTo("7500.0000");
        assertThat(accountCaptor.getValue().getPendingHoldBalance().getAmount()).isEqualByComparingTo("2500.0000");
    }

    @Test
    @DisplayName("Should reject authorization when available balance is insufficient")
    void shouldRejectWhenInsufficientFunds() {
        UUID accountId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        String idempotencyKey = "key_tx_insufficient";

        Card card = new Card(
                cardId, accountId, "tok_abc123", "411111******1111",
                12, 2030, CardStatus.ACTIVE, null, 0L, Instant.now(), Instant.now()
        );
        CardControls controls = CardControls.defaultControls(cardId, "INR");
        Account account = Account.create("ACC-987654", "INR", MonetaryAmount.of(500.0, "INR"));

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(cardControlsRepository.findByCardId(cardId)).thenReturn(Optional.of(controls));
        when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(account));

        AuthorizationRequest request = new AuthorizationRequest(
                idempotencyKey, cardId, null, MonetaryAmount.of(2500.0, "INR"),
                "Amazon Pay", true, false, false
        );

        assertThatThrownBy(() -> authorizationEngine.processAuthorization(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Insufficient available balance");
    }
}
