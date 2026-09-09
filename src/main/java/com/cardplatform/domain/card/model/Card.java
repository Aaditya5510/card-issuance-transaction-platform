package com.cardplatform.domain.card.model;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.CardExpiredException;
import com.cardplatform.common.exception.CardFrozenException;
import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Card Domain Aggregate Root.
 */
public class Card {

    private final UUID id;
    private final UUID accountId;
    private final String cardToken;
    private final String maskedPan;
    private final int expiryMonth;
    private final int expiryYear;
    private CardStatus status;
    private CardControls controls;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public Card(
            UUID id,
            UUID accountId,
            String cardToken,
            String maskedPan,
            int expiryMonth,
            int expiryYear,
            CardStatus status,
            CardControls controls,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (cardToken == null || cardToken.isBlank()) {
            throw new IllegalArgumentException("Card token cannot be blank");
        }
        if (maskedPan == null || maskedPan.isBlank()) {
            throw new IllegalArgumentException("Masked PAN cannot be blank");
        }
        if (expiryMonth < 1 || expiryMonth > 12) {
            throw new IllegalArgumentException("Expiry month must be between 1 and 12");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.accountId = accountId;
        this.cardToken = cardToken;
        this.maskedPan = maskedPan;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.status = status != null ? status : CardStatus.ACTIVE;
        this.controls = controls;
        this.version = version != null ? version : 0L;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Card issue(
            UUID accountId,
            String cardToken,
            String maskedPan,
            int expiryMonth,
            int expiryYear,
            String currency) {
        UUID cardId = UUID.randomUUID();
        CardControls controls = CardControls.defaultControls(cardId, currency);
        return new Card(
                cardId,
                accountId,
                cardToken,
                maskedPan,
                expiryMonth,
                expiryYear,
                CardStatus.ACTIVE,
                controls,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    public void validateForTransaction(MonetaryAmount amount, boolean isOnline, boolean isAtm, boolean isInternational) {
        if (status == CardStatus.FROZEN) {
            throw new CardFrozenException("Card is frozen. Card ID: " + id);
        }
        if (status != CardStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "Card is not active. Current status: " + status,
                    ErrorCode.CARD_INACTIVE
            );
        }
        if (isExpired()) {
            throw new CardExpiredException(
                    "Card has expired (expiry: " + expiryMonth + "/" + expiryYear + ")"
            );
        }
        if (controls != null) {
            controls.validateTransaction(amount, isOnline, isAtm, isInternational);
        }
    }

    public boolean isExpired() {
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        return (expiryYear < currentYear) || (expiryYear == currentYear && expiryMonth < currentMonth);
    }

    public void freeze() {
        if (status == CardStatus.TERMINATED) {
            throw new BusinessRuleViolationException("Cannot freeze a terminated card", ErrorCode.BUSINESS_RULE_VIOLATION);
        }
        this.status = CardStatus.FROZEN;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (status == CardStatus.TERMINATED) {
            throw new BusinessRuleViolationException("Cannot activate a terminated card", ErrorCode.BUSINESS_RULE_VIOLATION);
        }
        this.status = CardStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void terminate() {
        this.status = CardStatus.TERMINATED;
        this.updatedAt = Instant.now();
    }

    public void attachControls(CardControls controls) {
        this.controls = controls;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getCardToken() {
        return cardToken;
    }

    public String getMaskedPan() {
        return maskedPan;
    }

    public int getExpiryMonth() {
        return expiryMonth;
    }

    public int getExpiryYear() {
        return expiryYear;
    }

    public CardStatus getStatus() {
        return status;
    }

    public CardControls getControls() {
        return controls;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
