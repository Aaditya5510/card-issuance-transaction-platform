package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.model.CardStatus;
import com.cardplatform.domain.card.repository.CardControlsRepository;
import com.cardplatform.domain.card.repository.CardRepository;
import com.cardplatform.infrastructure.persistence.entity.AccountJpaEntity;
import com.cardplatform.infrastructure.persistence.entity.CardControlsJpaEntity;
import com.cardplatform.infrastructure.persistence.entity.CardJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaCardRepositoryAdapter implements CardRepository, CardControlsRepository {

    private final SpringDataCardRepository springDataCardRepository;
    private final SpringDataCardControlsRepository springDataCardControlsRepository;
    private final SpringDataAccountRepository springDataAccountRepository;

    public JpaCardRepositoryAdapter(
            SpringDataCardRepository springDataCardRepository,
            SpringDataCardControlsRepository springDataCardControlsRepository,
            SpringDataAccountRepository springDataAccountRepository) {
        this.springDataCardRepository = springDataCardRepository;
        this.springDataCardControlsRepository = springDataCardControlsRepository;
        this.springDataAccountRepository = springDataAccountRepository;
    }

    @Override
    public Optional<Card> findById(UUID id) {
        return springDataCardRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Card> findByCardToken(String cardToken) {
        return springDataCardRepository.findByCardToken(cardToken).map(this::toDomain);
    }

    @Override
    public List<Card> findAllByAccountId(UUID accountId) {
        return springDataCardRepository.findAllByAccountId(accountId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Card save(Card domain) {
        CardJpaEntity entity;
        if (domain.getId() != null && springDataCardRepository.existsById(domain.getId())) {
            entity = springDataCardRepository.findById(domain.getId()).orElse(new CardJpaEntity());
        } else {
            entity = new CardJpaEntity();
            entity.setId(domain.getId());
        }

        entity.setAccountId(domain.getAccountId());
        entity.setCardToken(domain.getCardToken());
        entity.setMaskedPan(domain.getMaskedPan());
        entity.setExpiryMonth(domain.getExpiryMonth());
        entity.setExpiryYear(domain.getExpiryYear());
        entity.setStatus(domain.getStatus().name());

        CardJpaEntity saved = springDataCardRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<CardControls> findByCardId(UUID cardId) {
        return springDataCardControlsRepository.findByCardId(cardId).map(this::toControlsDomain);
    }

    @Override
    public CardControls save(CardControls controls) {
        CardControlsJpaEntity entity = springDataCardControlsRepository.findByCardId(controls.getCardId())
                .orElse(new CardControlsJpaEntity());

        if (entity.getId() == null) {
            entity.setId(controls.getId());
        }
        entity.setCardId(controls.getCardId());
        entity.setDailyLimit(controls.getDailyLimit().getAmount());
        entity.setPerTxLimit(controls.getPerTxLimit().getAmount());
        entity.setOnlineEnabled(controls.isOnlineEnabled());
        entity.setAtmEnabled(controls.isAtmEnabled());
        entity.setInternationalEnabled(controls.isInternationalEnabled());

        CardControlsJpaEntity saved = springDataCardControlsRepository.save(entity);
        return toControlsDomain(saved);
    }

    private Card toDomain(CardJpaEntity entity) {
        CardControls controls = findByCardId(entity.getId()).orElse(null);
        return new Card(
                entity.getId(),
                entity.getAccountId(),
                entity.getCardToken(),
                entity.getMaskedPan(),
                entity.getExpiryMonth(),
                entity.getExpiryYear(),
                CardStatus.valueOf(entity.getStatus()),
                controls,
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CardControls toControlsDomain(CardControlsJpaEntity entity) {
        String currency = "USD";
        if (entity.getCardId() != null) {
            Optional<CardJpaEntity> cardOpt = springDataCardRepository.findById(entity.getCardId());
            if (cardOpt.isPresent()) {
                Optional<AccountJpaEntity> accountOpt = springDataAccountRepository.findById(cardOpt.get().getAccountId());
                if (accountOpt.isPresent()) {
                    currency = accountOpt.get().getCurrency();
                }
            }
        }
        return new CardControls(
                entity.getId(),
                entity.getCardId(),
                MonetaryAmount.of(entity.getDailyLimit(), currency),
                MonetaryAmount.of(entity.getPerTxLimit(), currency),
                entity.getOnlineEnabled(),
                entity.getAtmEnabled(),
                entity.getInternationalEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
