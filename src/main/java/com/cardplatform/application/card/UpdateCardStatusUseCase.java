package com.cardplatform.application.card;

import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardStatus;
import com.cardplatform.domain.card.repository.CardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use Case: Updates card operational lifecycle status (Activate, Freeze, Terminate).
 */
@Service
public class UpdateCardStatusUseCase {

    private final CardRepository cardRepository;

    public UpdateCardStatusUseCase(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    @Transactional
    public Card execute(UUID cardId, CardStatus newStatus) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with ID: " + cardId));

        switch (newStatus) {
            case ACTIVE -> card.activate();
            case FROZEN -> card.freeze();
            case TERMINATED -> card.terminate();
            default -> throw new IllegalArgumentException("Unsupported status transition: " + newStatus);
        }

        return cardRepository.save(card);
    }
}
