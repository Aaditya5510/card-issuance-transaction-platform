package com.cardplatform.integration.e2e;

import com.cardplatform.api.dto.ApiResponse;
import com.cardplatform.api.dto.PostJournalEntryRequest;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.infrastructure.persistence.entity.JournalEntryJpaEntity;
import com.cardplatform.infrastructure.persistence.entity.LedgerPostingJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class LedgerAuditVerificationTest extends AbstractE2ETest {

    @Test
    @DisplayName("E2E: Double-Entry Ledger Audit -> Multi-Legged Entries -> Compensating Reversals -> Strict Mathematical Invariants (Zero-Sum Conserved)")
    void testLedgerAuditAndMathematicalInvariants() {
        // 1. Setup: Provision 3 accounts
        // User A: $10,000.00 | User B: $5,000.00 | Settlement Pool: $50,000.00
        Account userA = createFundedAccount("ACC-AUDIT-A-" + UUID.randomUUID().toString().substring(0, 8), "USD", 10000.0);
        Account userB = createFundedAccount("ACC-AUDIT-B-" + UUID.randomUUID().toString().substring(0, 8), "USD", 5000.0);
        Account settlementPool = createFundedAccount("ACC-AUDIT-POOL-" + UUID.randomUUID().toString().substring(0, 8), "USD", 50000.0);

        UUID userAId = userA.getId();
        UUID userBId = userB.getId();
        UUID poolId = settlementPool.getId();

        // 2. Post Journal Entry 1: Balanced 2-Legged Transfer ($1,200.00 from User A to User B)
        PostJournalEntryRequest entry1Req = new PostJournalEntryRequest(
                UUID.randomUUID(),
                "idemp-transfer-" + UUID.randomUUID(),
                "corr-tx-001",
                "P2P Transfer User A to User B",
                List.of(
                        new PostJournalEntryRequest.PostingRequest(userAId, "DEBIT", BigDecimal.valueOf(1200.0), "USD", "Transfer to User B"),
                        new PostJournalEntryRequest.PostingRequest(userBId, "CREDIT", BigDecimal.valueOf(1200.0), "USD", "Transfer from User A")
                )
        );

        ResponseEntity<ApiResponse<PostJournalEntryRequest.Response>> entry1Resp = restTemplate.exchange(
                baseUrl("/api/v1/ledger/entries"),
                HttpMethod.POST,
                new HttpEntity<>(entry1Req, createHeaders(entry1Req.idempotencyKey())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(entry1Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entry1Resp.getBody()).isNotNull();
        assertThat(entry1Resp.getBody().isSuccess()).isTrue();
        UUID entry1Id = entry1Resp.getBody().getData().id();
        assertThat(entry1Id).isNotNull();

        // 3. Post Journal Entry 2: Balanced 3-Legged Split Settlement ($3,000.00 from Pool -> $2,000 to User A + $1,000 to User B)
        PostJournalEntryRequest entry2Req = new PostJournalEntryRequest(
                UUID.randomUUID(),
                "idemp-split-" + UUID.randomUUID(),
                "corr-tx-002",
                "Split Merchant Payout",
                List.of(
                        new PostJournalEntryRequest.PostingRequest(poolId, "DEBIT", BigDecimal.valueOf(3000.0), "USD", "Payout pool debit"),
                        new PostJournalEntryRequest.PostingRequest(userAId, "CREDIT", BigDecimal.valueOf(2000.0), "USD", "Split payout share A"),
                        new PostJournalEntryRequest.PostingRequest(userBId, "CREDIT", BigDecimal.valueOf(1000.0), "USD", "Split payout share B")
                )
        );

        ResponseEntity<ApiResponse<PostJournalEntryRequest.Response>> entry2Resp = restTemplate.exchange(
                baseUrl("/api/v1/ledger/entries"),
                HttpMethod.POST,
                new HttpEntity<>(entry2Req, createHeaders(entry2Req.idempotencyKey())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(entry2Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entry2Resp.getBody()).isNotNull();
        assertThat(entry2Resp.getBody().isSuccess()).isTrue();

        // 4. Post Compensating Reversal of Journal Entry 1 (Reverses the $1,200.00 Transfer)
        ResponseEntity<ApiResponse<PostJournalEntryRequest.Response>> reversalResp = restTemplate.exchange(
                baseUrl("/api/v1/ledger/entries/" + entry1Id + "/reversal?reason=Fraudulent+chargeback"),
                HttpMethod.POST,
                new HttpEntity<>(createHeaders("idemp-reversal-" + UUID.randomUUID())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(reversalResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reversalResp.getBody()).isNotNull();
        assertThat(reversalResp.getBody().isSuccess()).isTrue();
        UUID reversalEntryId = reversalResp.getBody().getData().id();
        assertThat(reversalEntryId).isNotEqualTo(entry1Id);

        // 5. Mathematical Invariant Verification:
        // Query all journal entries from the database and verify sum(Debits) == sum(Credits) for every entry
        List<JournalEntryJpaEntity> allJournalEntries = springDataJournalEntryRepository.findAll();
        assertThat(allJournalEntries).isNotEmpty();

        for (JournalEntryJpaEntity je : allJournalEntries) {
            List<LedgerPostingJpaEntity> postings = springDataLedgerPostingRepository.findByJournalEntryId(je.getId());
            assertThat(postings).isNotEmpty();

            BigDecimal totalDebits = postings.stream()
                    .filter(p -> "DEBIT".equalsIgnoreCase(p.getPostingType()))
                    .map(LedgerPostingJpaEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCredits = postings.stream()
                    .filter(p -> "CREDIT".equalsIgnoreCase(p.getPostingType()))
                    .map(LedgerPostingJpaEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Invariant: sum(Debits) MUST exactly equal sum(Credits)
            assertThat(totalDebits)
                    .as("Zero-sum invariant check for JournalEntry %s", je.getId())
                    .isEqualByComparingTo(totalCredits);

            // Audit Integrity: Each posting must have a valid non-null creation timestamp and accountId
            for (LedgerPostingJpaEntity p : postings) {
                assertThat(p.getCreatedAt()).isNotNull();
                assertThat(p.getAccountId()).isNotNull();
                assertThat(p.getSequenceNumber()).isGreaterThanOrEqualTo(1);
            }
        }

        // 6. Final Account Balance Integrity Verification:
        // - User A: 10,000 - 1,200 + 2,000 + 1,200 (reversal) = $12,000.00
        // - User B: 5,000 + 1,200 + 1,000 - 1,200 (reversal) = $6,000.00
        // - Pool: 50,000 - 3,000 = $47,000.00
        Account finalUserA = accountRepository.findById(userAId).orElseThrow();
        Account finalUserB = accountRepository.findById(userBId).orElseThrow();
        Account finalPool = accountRepository.findById(poolId).orElseThrow();

        assertThat(finalUserA.getAvailableBalance().getAmount()).isEqualByComparingTo("12000.0000");
        assertThat(finalUserA.getLedgerBalance().getAmount()).isEqualByComparingTo("12000.0000");

        assertThat(finalUserB.getAvailableBalance().getAmount()).isEqualByComparingTo("6000.0000");
        assertThat(finalUserB.getLedgerBalance().getAmount()).isEqualByComparingTo("6000.0000");

        assertThat(finalPool.getAvailableBalance().getAmount()).isEqualByComparingTo("47000.0000");
        assertThat(finalPool.getLedgerBalance().getAmount()).isEqualByComparingTo("47000.0000");

        // Total system money conservation:
        BigDecimal initialTotal = BigDecimal.valueOf(10000.0 + 5000.0 + 50000.0);
        BigDecimal finalTotal = finalUserA.getAvailableBalance().getAmount()
                .add(finalUserB.getAvailableBalance().getAmount())
                .add(finalPool.getAvailableBalance().getAmount());

        assertThat(finalTotal).isEqualByComparingTo(initialTotal);
    }
}
