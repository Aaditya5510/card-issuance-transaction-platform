package com.cardplatform.domain.ledger;

import com.cardplatform.common.exception.LedgerImbalanceException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.ledger.model.AccountType;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.LedgerPosting;
import com.cardplatform.domain.ledger.model.PostingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    @Test
    @DisplayName("Should successfully create balanced double-entry JournalEntry (2-leg)")
    void shouldCreateBalancedTwoLegJournalEntry() {
        UUID cashAccountId = UUID.randomUUID();
        UUID userAccountId = UUID.randomUUID();

        JournalEntry entry = JournalEntry.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("idemp_123")
                .correlationId("corr_123")
                .description("Deposit $100")
                .addDebit(cashAccountId, MonetaryAmount.of(100.0, "USD"), "Cash Inflow")
                .addCredit(userAccountId, MonetaryAmount.of(100.0, "USD"), "Cardholder Liability Increase")
                .build();

        assertThat(entry.getPostings()).hasSize(2);
        assertThat(entry.getDescription()).isEqualTo("Deposit $100");
        assertThat(entry.getIdempotencyKey()).isEqualTo("idemp_123");
        assertThat(entry.getCorrelationId()).isEqualTo("corr_123");
    }

    @Test
    @DisplayName("Should successfully create balanced multi-leg JournalEntry (3-leg fee split)")
    void shouldCreateBalancedMultiLegJournalEntry() {
        UUID customerAccountId = UUID.randomUUID();
        UUID merchantAccountId = UUID.randomUUID();
        UUID platformRevenueAccountId = UUID.randomUUID();

        // Customer pays $100: Merchant receives $97, Platform gets $3 fee
        JournalEntry entry = JournalEntry.builder()
                .description("Purchase with fee split")
                .addDebit(customerAccountId, MonetaryAmount.of(100.0, "USD"), "Customer Debit")
                .addCredit(merchantAccountId, MonetaryAmount.of(97.0, "USD"), "Merchant Payout")
                .addCredit(platformRevenueAccountId, MonetaryAmount.of(3.0, "USD"), "Platform Fee Revenue")
                .build();

        assertThat(entry.getPostings()).hasSize(3);
    }

    @Test
    @DisplayName("Should throw LedgerImbalanceException when total debits do not strictly equal total credits")
    void shouldRejectImbalancedJournalEntry() {
        UUID acc1 = UUID.randomUUID();
        UUID acc2 = UUID.randomUUID();

        assertThatThrownBy(() -> JournalEntry.builder()
                .description("Imbalanced Entry")
                .addDebit(acc1, MonetaryAmount.of(100.0, "USD"), "Debit 100")
                .addCredit(acc2, MonetaryAmount.of(90.0, "USD"), "Credit 90")
                .build())
                .isInstanceOf(LedgerImbalanceException.class)
                .hasMessageContaining("Mathematical Zero-Sum Invariant Failed")
                .hasMessageContaining("Total Debits (100.0000 USD)")
                .hasMessageContaining("Total Credits (90.0000 USD)");
    }

    @Test
    @DisplayName("Should throw LedgerImbalanceException when fewer than 2 postings are supplied")
    void shouldRejectSingleLegJournalEntry() {
        UUID acc1 = UUID.randomUUID();

        assertThatThrownBy(() -> JournalEntry.builder()
                .description("Single Leg")
                .addDebit(acc1, MonetaryAmount.of(100.0, "USD"), "Debit 100")
                .build())
                .isInstanceOf(LedgerImbalanceException.class)
                .hasMessageContaining("must consist of at least 2 legs");
    }

    @Test
    @DisplayName("Should throw LedgerImbalanceException when postings have mismatched currencies")
    void shouldRejectMismatchedCurrencyPostings() {
        UUID acc1 = UUID.randomUUID();
        UUID acc2 = UUID.randomUUID();

        List<LedgerPosting> postings = List.of(
                LedgerPosting.debit(acc1, MonetaryAmount.of(100.0, "USD"), 1, "USD Debit"),
                LedgerPosting.credit(acc2, MonetaryAmount.of(100.0, "EUR"), 2, "EUR Credit")
        );

        assertThatThrownBy(() -> JournalEntry.create("idemp_curr", "corr_curr", "FX mismatch", postings))
                .isInstanceOf(LedgerImbalanceException.class)
                .hasMessageContaining("must share the same currency");
    }

    @Test
    @DisplayName("Should guarantee strict immutability of postings list")
    void shouldEnforceImmutability() {
        UUID acc1 = UUID.randomUUID();
        UUID acc2 = UUID.randomUUID();

        JournalEntry entry = JournalEntry.builder()
                .description("Immutability Test")
                .addDebit(acc1, MonetaryAmount.of(50.0, "INR"), "Debit")
                .addCredit(acc2, MonetaryAmount.of(50.0, "INR"), "Credit")
                .build();

        assertThatThrownBy(() -> entry.getPostings().add(
                LedgerPosting.debit(acc1, MonetaryAmount.of(10.0, "INR"), 3, "New Leg")
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should correctly compute net balance according to normal balance rules across all AccountTypes")
    void shouldComputeAccountTypeBalances() {
        MonetaryAmount debits = MonetaryAmount.of(1000.0, "USD");
        MonetaryAmount credits = MonetaryAmount.of(400.0, "USD");

        // ASSET (Debit normal): Debits - Credits = 600
        assertThat(AccountType.ASSET.isDebitNormal()).isTrue();
        assertThat(AccountType.ASSET.calculateNetBalance(debits, credits).getAmount())
                .isEqualTo(new BigDecimal("600.0000"));

        // LIABILITY (Credit normal): Credits - Debits = -600
        assertThat(AccountType.LIABILITY.isCreditNormal()).isTrue();
        assertThat(AccountType.LIABILITY.calculateNetBalance(debits, credits).getAmount())
                .isEqualTo(new BigDecimal("-600.0000"));

        // EQUITY (Credit normal)
        assertThat(AccountType.EQUITY.isCreditNormal()).isTrue();

        // REVENUE (Credit normal)
        assertThat(AccountType.REVENUE.isCreditNormal()).isTrue();

        // EXPENSE (Debit normal): Debits - Credits = 600
        assertThat(AccountType.EXPENSE.isDebitNormal()).isTrue();
        assertThat(AccountType.EXPENSE.calculateNetBalance(debits, credits).getAmount())
                .isEqualTo(new BigDecimal("600.0000"));
    }
}
