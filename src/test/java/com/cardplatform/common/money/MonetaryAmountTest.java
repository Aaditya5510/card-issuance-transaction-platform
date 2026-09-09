package com.cardplatform.common.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonetaryAmountTest {

    @Test
    @DisplayName("Should scale amount to 4 decimal places with HALF_EVEN rounding")
    void shouldScaleToFourDecimals() {
        MonetaryAmount amount = MonetaryAmount.of(100.55555, "INR");
        assertThat(amount.getAmount()).isEqualTo(new BigDecimal("100.5556"));
        assertThat(amount.getCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("Should correctly perform addition and subtraction of same currency")
    void shouldAddAndSubtractSameCurrency() {
        MonetaryAmount m1 = MonetaryAmount.of(100.00, "INR");
        MonetaryAmount m2 = MonetaryAmount.of(50.50, "INR");

        MonetaryAmount sum = m1.add(m2);
        assertThat(sum.getAmount()).isEqualTo(new BigDecimal("150.5000"));

        MonetaryAmount diff = m1.subtract(m2);
        assertThat(diff.getAmount()).isEqualTo(new BigDecimal("49.5000"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when operating between mismatched currencies")
    void shouldRejectMismatchedCurrencies() {
        MonetaryAmount inr = MonetaryAmount.of(100.00, "INR");
        MonetaryAmount usd = MonetaryAmount.of(100.00, "USD");

        assertThatThrownBy(() -> inr.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    @DisplayName("Should correctly evaluate positive, negative, and zero checks")
    void shouldEvaluateSignChecks() {
        MonetaryAmount positive = MonetaryAmount.of(10.0, "USD");
        MonetaryAmount zero = MonetaryAmount.zero("USD");
        MonetaryAmount negative = MonetaryAmount.of(-5.0, "USD");

        assertThat(positive.isPositive()).isTrue();
        assertThat(zero.isZero()).isTrue();
        assertThat(negative.isNegative()).isTrue();
    }
}
