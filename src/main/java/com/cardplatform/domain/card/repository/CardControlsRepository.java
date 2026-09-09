package com.cardplatform.domain.card.repository;

import com.cardplatform.domain.card.model.CardControls;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository contract for Card Controls.
 */
public interface CardControlsRepository {

    Optional<CardControls> findByCardId(UUID cardId);

    CardControls save(CardControls cardControls);
}
