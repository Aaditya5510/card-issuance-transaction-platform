package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.infrastructure.persistence.entity.CardJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataCardRepository extends JpaRepository<CardJpaEntity, UUID> {

    Optional<CardJpaEntity> findByCardToken(String cardToken);

    List<CardJpaEntity> findAllByAccountId(UUID accountId);
}
