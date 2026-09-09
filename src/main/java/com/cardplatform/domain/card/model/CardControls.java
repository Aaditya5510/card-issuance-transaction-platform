package com.cardplatform.domain.card.model;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.CardLimitExceededException;
import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.UUID;

/**
 * Fine-grained transaction controls & spending limits for a Card.
 */
public class CardControls {

    private final UUID id;
    private final UUID cardId;
    private MonetaryAmount dailyLimit;
    private MonetaryAmount perTxLimit;
    private boolean onlineEnabled;
    private boolean atmEnabled;
    private boolean internationalEnabled;
    private final Instant createdAt;
    private Instant updatedAt;

    public CardControls(
            UUID id,
            UUID cardId,
            MonetaryAmount dailyLimit,
            MonetaryAmount perTxLimit,
            boolean onlineEnabled,
            boolean atmEnabled,
            boolean internationalEnabled,
            Instant createdAt,
            Instant updatedAt) {
        if (cardId == null) {
            throw new IllegalArgumentException("Card ID cannot be null");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.cardId = cardId;
        this.dailyLimit = dailyLimit != null ? dailyLimit : MonetaryAmount.of(50000.0, "INR");
        this.perTxLimit = perTxLimit != null ? perTxLimit : MonetaryAmount.of(10000.0, "INR");
        this.onlineEnabled = onlineEnabled;
        this.atmEnabled = atmEnabled;
        this.internationalEnabled = internationalEnabled;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static CardControls defaultControls(UUID cardId, String currency) {
        return new CardControls(
                UUID.randomUUID(),
                cardId,
                MonetaryAmount.of(50000.0, currency),
                MonetaryAmount.of(10000.0, currency),
                true,
                false,
                false,
                Instant.now(),
                Instant.now()
        );
    }

    public void validateTransaction(MonetaryAmount amount, boolean isOnline, boolean isAtm, boolean isInternational) {
        if (amount.isGreaterThan(perTxLimit)) {
            throw new CardLimitExceededException(
                    "Transaction amount exceeds single transaction limit: " + perTxLimit
            );
        }
        if (amount.isGreaterThan(dailyLimit)) {
            throw new CardLimitExceededException(
                    "Transaction amount exceeds daily limit: " + dailyLimit
            );
        }
        if (isOnline && !onlineEnabled) {
            throw new BusinessRuleViolationException("Online transactions are disabled on this card", ErrorCode.TRANSACTION_NOT_PERMITTED);
        }
        if (isAtm && !atmEnabled) {
            throw new BusinessRuleViolationException("ATM transactions are disabled on this card", ErrorCode.TRANSACTION_NOT_PERMITTED);
        }
        if (isInternational && !internationalEnabled) {
            throw new BusinessRuleViolationException("International transactions are disabled on this card", ErrorCode.TRANSACTION_NOT_PERMITTED);
        }
    }

    public void updateLimits(MonetaryAmount dailyLimit, MonetaryAmount perTxLimit) {
        if (dailyLimit != null) {
            if (!dailyLimit.isPositive()) {
                throw new IllegalArgumentException("Daily limit must be positive");
            }
            this.dailyLimit = dailyLimit;
        }
        if (perTxLimit != null) {
            if (!perTxLimit.isPositive()) {
                throw new IllegalArgumentException("Per-transaction limit must be positive");
            }
            this.perTxLimit = perTxLimit;
        }
        this.updatedAt = Instant.now();
    }

    public void updateChannels(Boolean onlineEnabled, Boolean atmEnabled, Boolean internationalEnabled) {
        if (onlineEnabled != null) this.onlineEnabled = onlineEnabled;
        if (atmEnabled != null) this.atmEnabled = atmEnabled;
        if (internationalEnabled != null) this.internationalEnabled = internationalEnabled;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public MonetaryAmount getDailyLimit() {
        return dailyLimit;
    }

    public MonetaryAmount getPerTxLimit() {
        return perTxLimit;
    }

    public boolean isOnlineEnabled() {
        return onlineEnabled;
    }

    public boolean isAtmEnabled() {
        return atmEnabled;
    }

    public boolean isInternationalEnabled() {
        return internationalEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
