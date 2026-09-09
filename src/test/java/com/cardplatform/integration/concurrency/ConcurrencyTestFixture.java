package com.cardplatform.integration.concurrency;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.model.CardStatus;
import com.cardplatform.domain.ledger.model.LedgerPosting;
import com.cardplatform.domain.ledger.model.PostingType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supporting fixture providing deterministic entity factories and financial invariant assertion helpers for stress testing.
 */
public final class ConcurrencyTestFixture {

    private ConcurrencyTestFixture() {}

    public static Account createFundedAccount(String accountNumber, String currency, double amount) {
        return Account.create(accountNumber, currency, MonetaryAmount.of(amount, currency));
    }

    public static Card createActiveCard(UUID cardId, UUID accountId) {
        return new Card(
                cardId,
                accountId,
                "tok_" + UUID.randomUUID().toString().substring(0, 8),
                "411111******1111",
                12,
                2035,
                CardStatus.ACTIVE,
                null,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    public static CardControls createGenerousControls(UUID cardId, String currency) {
        return new CardControls(
                UUID.randomUUID(),
                cardId,
                MonetaryAmount.of(1000000.0, currency),
                MonetaryAmount.of(500000.0, currency),
                true,
                true,
                true,
                Instant.now(),
                Instant.now()
        );
    }

    public static void assertZeroLedgerImbalance(List<LedgerPosting> postings) {
        if (postings == null || postings.isEmpty()) {
            return;
        }

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (LedgerPosting posting : postings) {
            if (posting.getType() == PostingType.DEBIT) {
                totalDebits = totalDebits.add(posting.getAmount().getAmount());
            } else if (posting.getType() == PostingType.CREDIT) {
                totalCredits = totalCredits.add(posting.getAmount().getAmount());
            }
        }

        assertThat(totalDebits)
                .as("Double-entry zero-sum invariant: total debits must equal total credits")
                .isEqualByComparingTo(totalCredits);
    }
}
