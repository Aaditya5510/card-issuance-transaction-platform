package com.cardplatform.domain.transaction.repository;

import com.cardplatform.domain.transaction.model.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Clean Architecture repository port for Transaction aggregates.
 */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(UUID id);

    Optional<Transaction> findByAuthorizationCode(String authorizationCode);

    List<Transaction> findByCardId(UUID cardId);

    List<Transaction> findByAccountId(UUID accountId);
}
