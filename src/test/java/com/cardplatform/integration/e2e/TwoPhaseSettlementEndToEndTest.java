package com.cardplatform.integration.e2e;

import com.cardplatform.api.dto.ApiResponse;
import com.cardplatform.api.dto.AuthorizeTransactionRequest;
import com.cardplatform.api.dto.CardResponse;
import com.cardplatform.api.dto.IssueCardRequest;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.transaction.model.AuthorizationHold;
import com.cardplatform.domain.transaction.model.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TwoPhaseSettlementEndToEndTest extends AbstractE2ETest {

    @Test
    @DisplayName("E2E: Two-Phase Transaction Flow -> Auth Hold ($150) -> Idempotent Replay -> Capture Settlement -> Final Balance Reconciliation")
    void testTwoPhaseAuthorizationAndSettlementEndToEnd() {
        // 1. Setup: Seed cardholder account with $500.00 and merchant clearing account with $0.00
        String cardholderAccNumber = "ACC-CH-" + UUID.randomUUID().toString().substring(0, 8);
        Account cardholderAccount = createFundedAccount(cardholderAccNumber, "USD", 500.0);
        UUID cardholderAccountId = cardholderAccount.getId();

        String merchantAccNumber = "ACC-MERCHANT-" + UUID.randomUUID().toString().substring(0, 8);
        Account merchantAccount = createFundedAccount(merchantAccNumber, "USD", 0.0);
        UUID merchantAccountId = merchantAccount.getId();

        // 2. Issue card for cardholder account
        IssueCardRequest issueReq = new IssueCardRequest(cardholderAccountId, "4242424242424242");
        ResponseEntity<ApiResponse<CardResponse>> issueResp = restTemplate.exchange(
                baseUrl("/api/v1/cards"),
                HttpMethod.POST,
                new HttpEntity<>(issueReq, createHeaders(null)),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(issueResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(issueResp.getBody()).isNotNull();
        UUID cardId = issueResp.getBody().getData().id();

        // 3. Phase 1: POST /api/v1/transactions/authorize for $150.00 with Idempotency-Key
        String idempotencyKey = "auth-tx-" + UUID.randomUUID();
        AuthorizeTransactionRequest authReq = new AuthorizeTransactionRequest(
                cardId,
                null,
                BigDecimal.valueOf(150.0),
                "USD",
                "Apple Store Fifth Ave",
                true,
                false,
                false
        );
        HttpEntity<AuthorizeTransactionRequest> authHttpEntity = new HttpEntity<>(authReq, createHeaders(idempotencyKey));

        ResponseEntity<ApiResponse<AuthorizeTransactionRequest.Response>> authResp = restTemplate.exchange(
                baseUrl("/api/v1/transactions/authorize"),
                HttpMethod.POST,
                authHttpEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(authResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authResp.getBody()).isNotNull();
        assertThat(authResp.getBody().isSuccess()).isTrue();

        AuthorizeTransactionRequest.Response authData = authResp.getBody().getData();
        assertThat(authData.status()).isEqualTo("AUTHORIZED");
        assertThat(authData.amount()).isEqualByComparingTo("150.0000");
        UUID authHoldId = authData.authorizationId();
        assertThat(authHoldId).isNotNull();

        // 4. Verify Phase 1 Database State:
        // - available_balance = $350.0000
        // - pending_hold_balance = $150.0000
        // - ledger_balance = $500.0000 (available + pending hold)
        Account postAuthAccount = accountRepository.findById(cardholderAccountId).orElseThrow();
        assertThat(postAuthAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("350.0000");
        assertThat(postAuthAccount.getPendingHoldBalance().getAmount()).isEqualByComparingTo("150.0000");
        assertThat(postAuthAccount.getLedgerBalance().getAmount()).isEqualByComparingTo("500.0000");

        AuthorizationHold holdInDb = authorizationRepository.findById(authHoldId).orElseThrow();
        assertThat(holdInDb.getStatus()).isEqualTo(TransactionStatus.AUTHORIZED);
        assertThat(holdInDb.getAmount().getAmount()).isEqualByComparingTo("150.0000");

        // 5. Idempotent Replay: Resend exact same authorization request with same Idempotency-Key
        ResponseEntity<ApiResponse<AuthorizeTransactionRequest.Response>> replayResp = restTemplate.exchange(
                baseUrl("/api/v1/transactions/authorize"),
                HttpMethod.POST,
                authHttpEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(replayResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayResp.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replayResp.getBody()).isNotNull();
        assertThat(replayResp.getBody().getData().authorizationId()).isEqualTo(authHoldId);

        // Verify Database State is strictly unmodified after idempotent replay (Zero double-spend)
        Account postReplayAccount = accountRepository.findById(cardholderAccountId).orElseThrow();
        assertThat(postReplayAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("350.0000");
        assertThat(postReplayAccount.getPendingHoldBalance().getAmount()).isEqualByComparingTo("150.0000");
        assertThat(postReplayAccount.getLedgerBalance().getAmount()).isEqualByComparingTo("500.0000");

        // 6. Phase 2: POST /api/v1/transactions/{authorizationId}/settle -> Capture and settle against merchant clearing account
        String settleUrl = baseUrl("/api/v1/transactions/" + authHoldId + "/settle?settlementAccountId=" + merchantAccountId);
        ResponseEntity<ApiResponse<UUID>> settleResp = restTemplate.exchange(
                settleUrl,
                HttpMethod.POST,
                new HttpEntity<>(createHeaders("settle-tx-" + UUID.randomUUID())),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(settleResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(settleResp.getBody()).isNotNull();
        assertThat(settleResp.getBody().isSuccess()).isTrue();
        UUID journalTxRefId = settleResp.getBody().getData();
        assertThat(journalTxRefId).isNotNull();

        // 7. Verify Phase 2 Database State:
        // - Hold status is CAPTURED
        // - Cardholder: available_balance = $350.0000, pending_hold_balance = $0.0000, ledger_balance = $350.0000
        // - Merchant: available_balance = $150.0000, ledger_balance = $150.0000
        AuthorizationHold settledHold = authorizationRepository.findById(authHoldId).orElseThrow();
        assertThat(settledHold.getStatus()).isEqualTo(TransactionStatus.CAPTURED);

        Account finalCardholderAccount = accountRepository.findById(cardholderAccountId).orElseThrow();
        assertThat(finalCardholderAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("350.0000");
        assertThat(finalCardholderAccount.getPendingHoldBalance().getAmount()).isEqualByComparingTo("0.0000");
        assertThat(finalCardholderAccount.getLedgerBalance().getAmount()).isEqualByComparingTo("350.0000");

        Account finalMerchantAccount = accountRepository.findById(merchantAccountId).orElseThrow();
        assertThat(finalMerchantAccount.getAvailableBalance().getAmount()).isEqualByComparingTo("150.0000");
        assertThat(finalMerchantAccount.getLedgerBalance().getAmount()).isEqualByComparingTo("150.0000");
    }
}
