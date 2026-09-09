package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.transaction.model.AuthorizationHold;
import com.cardplatform.domain.transaction.model.TransactionStatus;
import com.cardplatform.domain.transaction.repository.AuthorizationRepository;
import com.cardplatform.infrastructure.persistence.entity.AuthorizationJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaAuthorizationRepositoryAdapter implements AuthorizationRepository {

    private final SpringDataAuthorizationRepository springDataAuthorizationRepository;

    public JpaAuthorizationRepositoryAdapter(SpringDataAuthorizationRepository springDataAuthorizationRepository) {
        this.springDataAuthorizationRepository = springDataAuthorizationRepository;
    }

    @Override
    public Optional<AuthorizationHold> findById(UUID id) {
        return springDataAuthorizationRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<AuthorizationHold> findByIdempotencyKey(String idempotencyKey) {
        return springDataAuthorizationRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public AuthorizationHold save(AuthorizationHold domain) {
        AuthorizationJpaEntity entity;
        if (domain.getId() != null && springDataAuthorizationRepository.existsById(domain.getId())) {
            entity = springDataAuthorizationRepository.findById(domain.getId()).orElse(new AuthorizationJpaEntity());
        } else {
            entity = new AuthorizationJpaEntity();
            entity.setId(domain.getId());
        }

        entity.setCardId(domain.getCardId());
        entity.setAccountId(domain.getAccountId());
        entity.setIdempotencyKey(domain.getIdempotencyKey());
        entity.setAmount(domain.getAmount().getAmount());
        entity.setCurrency(domain.getAmount().getCurrency());
        entity.setMerchantName(domain.getMerchantName());
        entity.setStatus(domain.getStatus().name());
        entity.setExpiresAt(domain.getExpiresAt());

        AuthorizationJpaEntity saved = springDataAuthorizationRepository.save(entity);
        return toDomain(saved);
    }

    private AuthorizationHold toDomain(AuthorizationJpaEntity entity) {
        return new AuthorizationHold(
                entity.getId(),
                entity.getCardId(),
                entity.getAccountId(),
                entity.getIdempotencyKey(),
                MonetaryAmount.of(entity.getAmount(), entity.getCurrency()),
                entity.getMerchantName(),
                TransactionStatus.valueOf(entity.getStatus()),
                entity.getExpiresAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
