package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.infrastructure.persistence.entity.JournalEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataJournalEntryRepository extends JpaRepository<JournalEntryJpaEntity, UUID> {

    Optional<JournalEntryJpaEntity> findByCorrelationId(String correlationId);

    Optional<JournalEntryJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
