package com.cardplatform.common.money;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable financial Value Object representing a currency-denominated amount.
 * Aligned with FinTech ledger specifications (fixed 4 decimal precision).
 */
public final class MonetaryAmount implements Comparable<MonetaryAmount> {

    public static final int DEFAULT_SCALE = 4;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_EVEN;
    public static final String DEFAULT_CURRENCY = "INR";

    private final BigDecimal amount;
    private final String currency;

    @JsonCreator
    public MonetaryAmount(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or blank");
        }
        // Normalize currency code & amount scale
        this.currency = validateAndNormalizeCurrency(currency);
        this.amount = amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    public static MonetaryAmount of(BigDecimal amount, String currency) {
        return new MonetaryAmount(amount, currency);
    }

    public static MonetaryAmount of(double amount, String currency) {
        return new MonetaryAmount(BigDecimal.valueOf(amount), currency);
    }

    public static MonetaryAmount of(long amount, String currency) {
        return new MonetaryAmount(BigDecimal.valueOf(amount), currency);
    }

    public static MonetaryAmount zero(String currency) {
        return new MonetaryAmount(BigDecimal.ZERO, currency);
    }

    public static MonetaryAmount zero() {
        return zero(DEFAULT_CURRENCY);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public MonetaryAmount add(MonetaryAmount other) {
        assertSameCurrency(other);
        return new MonetaryAmount(this.amount.add(other.amount), this.currency);
    }

    public MonetaryAmount subtract(MonetaryAmount other) {
        assertSameCurrency(other);
        return new MonetaryAmount(this.amount.subtract(other.amount), this.currency);
    }

    public MonetaryAmount multiply(BigDecimal multiplier) {
        if (multiplier == null) {
            throw new IllegalArgumentException("Multiplier cannot be null");
        }
        return new MonetaryAmount(this.amount.multiply(multiplier), this.currency);
    }

    public MonetaryAmount negate() {
        return new MonetaryAmount(this.amount.negate(), this.currency);
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isGreaterThan(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.compareTo(other) > 0;
    }

    public boolean isGreaterThanOrEqual(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.compareTo(other) >= 0;
    }

    public boolean isLessThan(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.compareTo(other) < 0;
    }

    public boolean isLessThanOrEqual(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.compareTo(other) <= 0;
    }

    private void assertSameCurrency(MonetaryAmount other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot operate on null MonetaryAmount");
        }
        if (!this.currency.equalsIgnoreCase(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: cannot operate between " + this.currency + " and " + other.currency);
        }
    }

    private static String validateAndNormalizeCurrency(String currencyCode) {
        String normalized = currencyCode.trim().toUpperCase();
        try {
            Currency.getInstance(normalized);
            return normalized;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ISO 4217 currency code: " + currencyCode, e);
        }
    }

    @Override
    public int compareTo(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MonetaryAmount that = (MonetaryAmount) o;
        return this.amount.compareTo(that.amount) == 0 &&
                this.currency.equalsIgnoreCase(that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency.toUpperCase());
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
