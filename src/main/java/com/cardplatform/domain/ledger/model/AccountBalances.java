package com.cardplatform.domain.ledger.model;

import com.cardplatform.common.money.MonetaryAmount;

import java.util.UUID;

/**
 * Value object representing the balances of an account.
 */
public record AccountBalances(
        UUID accountId,
        MonetaryAmount availableBalance,
        MonetaryAmount pendingHoldBalance,
        MonetaryAmount totalBalance
) {
    public static AccountBalances of(UUID accountId, MonetaryAmount available, MonetaryAmount pending) {
        return new AccountBalances(accountId, available, pending, available.add(pending));
    }
}
