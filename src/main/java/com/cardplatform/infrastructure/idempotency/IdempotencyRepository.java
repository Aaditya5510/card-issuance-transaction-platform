package com.cardplatform.infrastructure.idempotency;

import java.util.Optional;

/**
 * Technical repository port for storing and querying Idempotency records.
 */
public interface IdempotencyRepository {

    Optional<IdempotencyRecord> findByKey(String idempotencyKey);

    IdempotencyRecord save(IdempotencyRecord record);

    void deleteByKey(String idempotencyKey);
}
