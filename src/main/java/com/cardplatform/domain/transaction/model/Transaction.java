package com.cardplatform.domain.transaction.model;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transaction Aggregate Root.
 * Represents financial swipe transactions, authorization holds, settlements, and lifecycle state transitions.
 */
public class Transaction {

    private final UUID id;
    private final UUID cardId;
    private final UUID accountId;
    private final MonetaryAmount amount;
    private final TransactionType type;
    private TransactionStatus status;
    private final String authorizationCode;
    private final String merchantId;
    private final String merchantCategoryCode;
    private final Instant expiresAt;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public Transaction(
            UUID id,
            UUID cardId,
            UUID accountId,
            MonetaryAmount amount,
            TransactionType type,
            TransactionStatus status,
            String authorizationCode,
            String merchantId,
            String merchantCategoryCode,
            Instant expiresAt,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        if (cardId == null) {
            throw new IllegalArgumentException("Card ID cannot be null");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.cardId = cardId;
        this.accountId = accountId;
        this.amount = amount;
        this.type = type != null ? type : TransactionType.AUTHORIZATION;
        this.status = status != null ? status : TransactionStatus.PENDING;
        this.authorizationCode = authorizationCode;
        this.merchantId = merchantId != null ? merchantId : "UNKNOWN";
        this.merchantCategoryCode = merchantCategoryCode != null ? merchantCategoryCode : "0000";
        this.expiresAt = expiresAt;
        this.version = version != null ? version : 0L;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Transaction createAuthorization(
            UUID cardId,
            UUID accountId,
            MonetaryAmount amount,
            String authorizationCode,
            String merchantId,
            String merchantCategoryCode,
            Instant expiresAt) {
        return new Transaction(
                UUID.randomUUID(),
                cardId,
                accountId,
                amount,
                TransactionType.AUTHORIZATION,
                TransactionStatus.APPROVED,
                authorizationCode,
                merchantId,
                merchantCategoryCode,
                expiresAt,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    public void settle() {
        if (status != TransactionStatus.APPROVED && status != TransactionStatus.AUTHORIZED && status != TransactionStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Cannot settle transaction in current status: " + status,
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        this.status = TransactionStatus.SETTLED;
        this.updatedAt = Instant.now();
    }

    public void voidTransaction() {
        if (status != TransactionStatus.APPROVED && status != TransactionStatus.AUTHORIZED && status != TransactionStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Cannot void transaction in current status: " + status,
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        this.status = TransactionStatus.VOIDED;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        if (status != TransactionStatus.APPROVED && status != TransactionStatus.AUTHORIZED && status != TransactionStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Cannot expire transaction in current status: " + status,
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        this.status = TransactionStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public void decline() {
        this.status = TransactionStatus.DECLINED;
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

    public MonetaryAmount getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getMerchantCategoryCode() {
        return merchantCategoryCode;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
