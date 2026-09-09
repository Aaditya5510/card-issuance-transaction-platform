package com.cardplatform.application.card;

import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.repository.CardControlsRepository;
import com.cardplatform.domain.card.repository.CardRepository;
import com.cardplatform.infrastructure.security.PanMaskingUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Use Case: Issues a new debit/credit card linked to an active account.
 */
@Service
public class IssueCardUseCase {

    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final CardControlsRepository cardControlsRepository;

    public IssueCardUseCase(
            AccountRepository accountRepository,
            CardRepository cardRepository,
            CardControlsRepository cardControlsRepository) {
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.cardControlsRepository = cardControlsRepository;
    }

    @Transactional
    public Card execute(UUID accountId, String rawPan) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        String cardToken = "tok_" + UUID.randomUUID().toString().replace("-", "");
        String maskedPan = PanMaskingUtil.maskPan(rawPan);

        LocalDate now = LocalDate.now();
        int expiryMonth = now.getMonthValue();
        int expiryYear = now.getYear() + 4; // 4-year card validity

        Card card = Card.issue(account.getId(), cardToken, maskedPan, expiryMonth, expiryYear, account.getCurrency());
        Card savedCard = cardRepository.save(card);

        CardControls controls = card.getControls();
        if (controls != null) {
            cardControlsRepository.save(controls);
        }

        return savedCard;
    }
}
