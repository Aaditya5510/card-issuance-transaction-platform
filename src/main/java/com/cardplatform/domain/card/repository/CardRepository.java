package com.cardplatform.domain.card.repository;

import com.cardplatform.domain.card.model.Card;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository contract for Cards.
 */
public interface CardRepository {

    Optional<Card> findById(UUID id);

    Optional<Card> findByCardToken(String cardToken);

    List<Card> findAllByAccountId(UUID accountId);

    Card save(Card card);
}
