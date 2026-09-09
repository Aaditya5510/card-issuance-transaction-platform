package com.cardplatform.domain.transaction.repository;

import com.cardplatform.domain.transaction.model.TransactionHold;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Clean Architecture repository port for TransactionHold entities.
 */
public interface TransactionHoldRepository {

    TransactionHold save(TransactionHold hold);

    Optional<TransactionHold> findById(UUID id);

    Optional<TransactionHold> findByTransactionId(UUID transactionId);

    List<TransactionHold> findExpiredActiveHolds(Instant now);

    List<TransactionHold> findByAccountId(UUID accountId);
}
