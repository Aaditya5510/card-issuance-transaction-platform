package com.cardplatform.domain.transaction.model;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.UUID;

/**
 * Authorization Hold Aggregate representing a 2-phase authorization hold placed on an account.
 */
public class AuthorizationHold {

    private final UUID id;
    private final UUID cardId;
    private final UUID accountId;
    private final String idempotencyKey;
    private final MonetaryAmount amount;
    private final String merchantName;
    private TransactionStatus status;
    private final Instant expiresAt;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public AuthorizationHold(
            UUID id,
            UUID cardId,
            UUID accountId,
            String idempotencyKey,
            MonetaryAmount amount,
            String merchantName,
            TransactionStatus status,
            Instant expiresAt,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        if (cardId == null || accountId == null) {
            throw new IllegalArgumentException("Card ID and Account ID are required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.cardId = cardId;
        this.accountId = accountId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.merchantName = merchantName != null ? merchantName : "UNKNOWN";
        this.status = status != null ? status : TransactionStatus.PENDING;
        this.expiresAt = expiresAt != null ? expiresAt : Instant.now().plusSeconds(7 * 24 * 3600); // 7 day default hold
        this.version = version != null ? version : 0L;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static AuthorizationHold createPending(
            UUID cardId,
            UUID accountId,
            String idempotencyKey,
            MonetaryAmount amount,
            String merchantName,
            Instant expiresAt) {
        return new AuthorizationHold(
                UUID.randomUUID(),
                cardId,
                accountId,
                idempotencyKey,
                amount,
                merchantName,
                TransactionStatus.PENDING,
                expiresAt,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    public void authorize() {
        if (this.status != TransactionStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Cannot authorize transaction in status: " + this.status,
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        this.status = TransactionStatus.AUTHORIZED;
        this.updatedAt = Instant.now();
    }

    public void capture() {
        if (this.status != TransactionStatus.AUTHORIZED) {
            throw new BusinessRuleViolationException(
                    "Cannot capture transaction in status: " + this.status,
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        this.status = TransactionStatus.CAPTURED;
        this.updatedAt = Instant.now();
    }

    public void decline() {
        this.status = TransactionStatus.DECLINED;
        this.updatedAt = Instant.now();
    }

    public void reverse() {
        if (this.status != TransactionStatus.AUTHORIZED && this.status != TransactionStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Cannot reverse transaction in status: " + this.status,
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        this.status = TransactionStatus.REVERSED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public MonetaryAmount getAmount() {
        return amount;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
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
