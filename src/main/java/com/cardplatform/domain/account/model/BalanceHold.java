package com.cardplatform.domain.account.model;

import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of a balance hold placed during two-phase transaction authorization.
 */
public class BalanceHold {

    private final UUID holdId;
    private final UUID accountId;
    private final MonetaryAmount amount;
    private final String merchantName;
    private final Instant expiresAt;
    private boolean active;

    public BalanceHold(UUID holdId, UUID accountId, MonetaryAmount amount, String merchantName, Instant expiresAt) {
        if (holdId == null || accountId == null || amount == null || expiresAt == null) {
            throw new IllegalArgumentException("Hold attributes cannot be null");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Hold amount must be positive");
        }
        this.holdId = holdId;
        this.accountId = accountId;
        this.amount = amount;
        this.merchantName = merchantName;
        this.expiresAt = expiresAt;
        this.active = true;
    }

    public UUID getHoldId() {
        return holdId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public MonetaryAmount getAmount() {
        return amount;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void release() {
        this.active = false;
    }
}
