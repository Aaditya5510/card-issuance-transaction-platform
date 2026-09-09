package com.cardplatform.domain.transaction.repository;

import com.cardplatform.domain.transaction.model.AuthorizationHold;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository contract for Authorization transactions and holds.
 */
public interface AuthorizationRepository {

    Optional<AuthorizationHold> findById(UUID id);

    Optional<AuthorizationHold> findByIdempotencyKey(String idempotencyKey);

    AuthorizationHold save(AuthorizationHold authorizationHold);
}
