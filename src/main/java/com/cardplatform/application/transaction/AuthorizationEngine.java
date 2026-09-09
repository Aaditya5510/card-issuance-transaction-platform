package com.cardplatform.application.transaction;

import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.repository.CardControlsRepository;
import com.cardplatform.domain.card.repository.CardRepository;
import com.cardplatform.domain.transaction.model.AuthorizationHold;
import com.cardplatform.domain.transaction.model.AuthorizationRequest;
import com.cardplatform.domain.transaction.repository.AuthorizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * High-throughput Two-Phase Card Authorization Engine.
 * Phase 1: Real-time balance hold reservation & card control verification.
 */
@Service
public class AuthorizationEngine {

    private final CardRepository cardRepository;
    private final CardControlsRepository cardControlsRepository;
    private final AccountRepository accountRepository;
    private final AuthorizationRepository authorizationRepository;

    public AuthorizationEngine(
            CardRepository cardRepository,
            CardControlsRepository cardControlsRepository,
            AccountRepository accountRepository,
            AuthorizationRepository authorizationRepository) {
        this.cardRepository = cardRepository;
        this.cardControlsRepository = cardControlsRepository;
        this.accountRepository = accountRepository;
        this.authorizationRepository = authorizationRepository;
    }

    @Transactional
    public AuthorizationHold processAuthorization(AuthorizationRequest request) {
        // Idempotency check on existing authorization hold
        var existingAuth = authorizationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingAuth.isPresent()) {
            return existingAuth.get();
        }

        Card card;
        if (request.cardId() != null) {
            card = cardRepository.findById(request.cardId())
                    .orElseThrow(() -> new ResourceNotFoundException("Card not found with ID: " + request.cardId()));
        } else if (request.cardToken() != null) {
            card = cardRepository.findByCardToken(request.cardToken())
                    .orElseThrow(() -> new ResourceNotFoundException("Card not found with token: " + request.cardToken()));
        } else {
            throw new IllegalArgumentException("Either cardId or cardToken must be supplied");
        }

        CardControls controls = cardControlsRepository.findByCardId(card.getId()).orElse(null);
        card.attachControls(controls);

        // Invariant checks: Card active, not expired, channel and limit rules
        card.validateForTransaction(request.amount(), request.isOnline(), request.isAtm(), request.isInternational());

        Account account = accountRepository.findByIdWithLock(card.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for card: " + card.getId()));

        // Invariant check: Available balance >= hold amount
        account.placeHold(request.amount());
        accountRepository.save(account);

        AuthorizationHold hold = AuthorizationHold.createPending(
                card.getId(),
                account.getId(),
                request.idempotencyKey(),
                request.amount(),
                request.merchantName(),
                Instant.now().plusSeconds(7 * 24 * 3600)
        );
        hold.authorize();

        return authorizationRepository.save(hold);
    }
}
