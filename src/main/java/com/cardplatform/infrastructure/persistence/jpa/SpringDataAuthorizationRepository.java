package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.infrastructure.persistence.entity.AuthorizationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataAuthorizationRepository extends JpaRepository<AuthorizationJpaEntity, UUID> {

    Optional<AuthorizationJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
