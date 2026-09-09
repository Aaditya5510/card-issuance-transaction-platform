package com.cardplatform.infrastructure.idempotency;

import com.cardplatform.infrastructure.persistence.entity.IdempotencyRecordJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaIdempotencyRepositoryAdapter implements IdempotencyRepository {

    private final SpringDataIdempotencyRepository repository;

    public JpaIdempotencyRepositoryAdapter(SpringDataIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toRecord);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord domain) {
        IdempotencyRecordJpaEntity entity;
        if (domain.id() != null && repository.existsById(domain.id())) {
            entity = repository.findById(domain.id()).orElse(new IdempotencyRecordJpaEntity());
        } else if (repository.existsByIdempotencyKey(domain.idempotencyKey())) {
            entity = repository.findByIdempotencyKey(domain.idempotencyKey()).orElse(new IdempotencyRecordJpaEntity());
        } else {
            entity = new IdempotencyRecordJpaEntity();
            entity.setId(domain.id());
        }

        entity.setIdempotencyKey(domain.idempotencyKey());
        entity.setRequestHash(domain.requestHash());
        entity.setClientOrAccountId(domain.clientOrAccountId());
        entity.setResponseCode(domain.responseCode());
        entity.setResponseBody(domain.responseBody());
        entity.setStatus(domain.status().name());
        entity.setExpiresAt(domain.expiresAt());

        IdempotencyRecordJpaEntity saved = repository.save(entity);
        return toRecord(saved);
    }

    @Override
    public void deleteByKey(String idempotencyKey) {
        repository.deleteByIdempotencyKey(idempotencyKey);
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
