package com.cardplatform.infrastructure.idempotency;

import com.cardplatform.common.exception.IdempotencyInProgressException;
import com.cardplatform.common.exception.IdempotencyPayloadMismatchException;
import com.cardplatform.infrastructure.persistence.entity.IdempotencyRecordJpaEntity;
import com.cardplatform.infrastructure.security.SecurityKeyHashingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Technical service enforcing IETF standard Idempotency-Key guarantees across FinTech operations.
 * Implements atomic state transitions: IN_PROGRESS -> RESOLVED / FAILED.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final SpringDataIdempotencyRepository repository;

    public IdempotencyService(SpringDataIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<IdempotencyRecord> checkOrReserve(String idempotencyKey, String rawPayload) {
        return checkOrReserve(idempotencyKey, rawPayload, null);
    }

    @Transactional
    public Optional<IdempotencyRecord> checkOrReserve(String idempotencyKey, String rawPayload, String clientOrAccountId) {
        String requestHash = SecurityKeyHashingUtil.sha256Hex(rawPayload);
        return checkOrReserveWithHash(idempotencyKey, requestHash, clientOrAccountId);
    }

    @Transactional
    public Optional<IdempotencyRecord> checkOrReserveWithHash(String idempotencyKey, String requestHash, String clientOrAccountId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or blank");
        }

        Optional<IdempotencyRecordJpaEntity> existingOpt = repository.findByIdempotencyKey(idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecordJpaEntity entity = existingOpt.get();
            IdempotencyStatus currentStatus = IdempotencyStatus.fromString(entity.getStatus());

            // 1. Validate payload fingerprint
            if (entity.getRequestHash() != null && !entity.getRequestHash().equalsIgnoreCase(requestHash)) {
                log.warn("Idempotency key '{}' reused with mismatched payload hash. Expected={}, Provided={}",
                        idempotencyKey, entity.getRequestHash(), requestHash);
                throw new IdempotencyPayloadMismatchException(
                        "Idempotency key '" + idempotencyKey + "' was previously used with a different request payload"
                );
            }

            // 2. State Machine Checks
            if (currentStatus == IdempotencyStatus.IN_PROGRESS) {
                log.warn("Concurrent in-flight request detected for idempotency key '{}'", idempotencyKey);
                throw new IdempotencyInProgressException(
                        "A request with the idempotency key '" + idempotencyKey + "' is currently in progress. Please retry shortly."
                );
            }

            if (currentStatus == IdempotencyStatus.RESOLVED) {
                log.info("Idempotency match found for key '{}'. Returning cached response (status={})",
                        idempotencyKey, entity.getResponseCode());
                return Optional.of(toRecord(entity));
            }

            if (currentStatus == IdempotencyStatus.FAILED) {
                log.info("Retrying previously failed request for idempotency key '{}'", idempotencyKey);
                entity.setStatus(IdempotencyStatus.IN_PROGRESS.name());
                entity.setResponseCode(null);
                entity.setResponseBody(null);
                entity.setUpdatedAt(Instant.now());
                repository.save(entity);
                return Optional.empty();
            }
        }

        // 3. Atomically reserve key as IN_PROGRESS
        IdempotencyRecordJpaEntity newRecord = new IdempotencyRecordJpaEntity();
        newRecord.setIdempotencyKey(idempotencyKey);
        newRecord.setRequestHash(requestHash);
        newRecord.setClientOrAccountId(clientOrAccountId);
        newRecord.setStatus(IdempotencyStatus.IN_PROGRESS.name());
        newRecord.setExpiresAt(Instant.now().plusSeconds(24 * 3600)); // 24-hour TTL

        try {
            repository.save(newRecord);
            log.info("Reserved idempotency key '{}' with status IN_PROGRESS", idempotencyKey);
        } catch (Exception ex) {
            // Concurrent insert race condition caught by DB unique constraint
            log.warn("Concurrency collision on idempotency key '{}': {}", idempotencyKey, ex.getMessage());
            Optional<IdempotencyRecordJpaEntity> concurrent = repository.findByIdempotencyKey(idempotencyKey);
            if (concurrent.isPresent()) {
                IdempotencyRecordJpaEntity cEntity = concurrent.get();
                if (cEntity.getRequestHash() != null && !cEntity.getRequestHash().equalsIgnoreCase(requestHash)) {
                    throw new IdempotencyPayloadMismatchException(
                            "Idempotency key '" + idempotencyKey + "' was previously used with a different request payload"
                    );
                }
                throw new IdempotencyInProgressException(
                        "A request with the idempotency key '" + idempotencyKey + "' is currently in progress."
                );
            }
            throw ex;
        }

        return Optional.empty();
    }

    @Transactional
    public void recordResponse(String idempotencyKey, int statusCode, String responseBody) {
        resolve(idempotencyKey, statusCode, responseBody);
    }

    @Transactional
    public void resolve(String idempotencyKey, int statusCode, String responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        repository.findByIdempotencyKey(idempotencyKey).ifPresent(entity -> {
            entity.setResponseCode(statusCode);
            entity.setResponseBody(responseBody);
            entity.setStatus(IdempotencyStatus.RESOLVED.name());
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
            log.info("Resolved idempotency key '{}' with HTTP status {}", idempotencyKey, statusCode);
        });
    }

    @Transactional
    public void markFailed(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        repository.findByIdempotencyKey(idempotencyKey).ifPresent(entity -> {
            entity.setStatus(IdempotencyStatus.FAILED.name());
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
            log.warn("Marked idempotency key '{}' as FAILED", idempotencyKey);
        });
    }

    @Transactional
    public void releaseKey(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            repository.deleteByIdempotencyKey(idempotencyKey);
            log.info("Deleted/Released idempotency key '{}'", idempotencyKey);
        }
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toRecord);
    }

    private IdempotencyRecord toRecord(IdempotencyRecordJpaEntity entity) {
        return new IdempotencyRecord(
                entity.getId(),
                entity.getIdempotencyKey(),
                entity.getRequestHash(),
                entity.getClientOrAccountId(),
                IdempotencyStatus.fromString(entity.getStatus()),
                entity.getResponseCode(),
                entity.getResponseBody(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getUpdatedAt()
        );
    }
}
