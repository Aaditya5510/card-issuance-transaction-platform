package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.infrastructure.persistence.entity.TransactionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID> {

    Optional<TransactionJpaEntity> findByAuthorizationCode(String authorizationCode);

    List<TransactionJpaEntity> findByCardId(UUID cardId);

    List<TransactionJpaEntity> findByAccountId(UUID accountId);
}
