package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.infrastructure.persistence.entity.CardControlsJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataCardControlsRepository extends JpaRepository<CardControlsJpaEntity, UUID> {

    Optional<CardControlsJpaEntity> findByCardId(UUID cardId);
}
