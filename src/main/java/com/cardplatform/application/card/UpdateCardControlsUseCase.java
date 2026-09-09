package com.cardplatform.application.card;

import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.repository.CardControlsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use Case: Updates card spending limits and channel rules.
 */
@Service
public class UpdateCardControlsUseCase {

    private final CardControlsRepository cardControlsRepository;

    public UpdateCardControlsUseCase(CardControlsRepository cardControlsRepository) {
        this.cardControlsRepository = cardControlsRepository;
    }

    @Transactional
    public CardControls execute(
            UUID cardId,
            MonetaryAmount dailyLimit,
            MonetaryAmount perTxLimit,
            Boolean onlineEnabled,
            Boolean atmEnabled,
            Boolean internationalEnabled) {
        CardControls controls = cardControlsRepository.findByCardId(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card controls not found for card ID: " + cardId));

        if (dailyLimit != null || perTxLimit != null) {
            controls.updateLimits(dailyLimit, perTxLimit);
        }
        if (onlineEnabled != null || atmEnabled != null || internationalEnabled != null) {
            controls.updateChannels(onlineEnabled, atmEnabled, internationalEnabled);
        }

        return cardControlsRepository.save(controls);
    }
}
