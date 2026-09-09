package com.cardplatform.integration.e2e;

import com.cardplatform.api.dto.ApiResponse;
import com.cardplatform.api.dto.AuthorizeTransactionRequest;
import com.cardplatform.api.dto.CardControlsResponse;
import com.cardplatform.api.dto.CardResponse;
import com.cardplatform.api.dto.IssueCardRequest;
import com.cardplatform.api.dto.UpdateCardControlsRequest;
import com.cardplatform.api.dto.UpdateCardStatusRequest;
import com.cardplatform.domain.account.model.Account;
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

public class CardLifecycleEndToEndTest extends AbstractE2ETest {

    @Test
    @DisplayName("E2E: Complete Card Lifecycle -> Issue -> Configure Controls -> Freeze -> Attempt Declined Swipe -> Re-activate -> Approved Swipe -> Terminate")
    void testCompleteCardLifecycleEndToEnd() {
        // 1. Provision funded funding account
        String accNumber = "ACC-E2E-CARD-" + UUID.randomUUID().toString().substring(0, 8);
        Account account = createFundedAccount(accNumber, "USD", 1000.0);
        UUID accountId = account.getId();

        // 2. POST /api/v1/cards -> Issue new card
        String rawPan = "4111111111111234";
        IssueCardRequest issueReq = new IssueCardRequest(accountId, rawPan);
        HttpEntity<IssueCardRequest> issueHttpEntity = new HttpEntity<>(issueReq, createHeaders(null));

        ResponseEntity<ApiResponse<CardResponse>> issueResp = restTemplate.exchange(
                baseUrl("/api/v1/cards"),
                HttpMethod.POST,
                issueHttpEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(issueResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(issueResp.getBody()).isNotNull();
        assertThat(issueResp.getBody().isSuccess()).isTrue();

        CardResponse cardData = issueResp.getBody().getData();
        assertThat(cardData).isNotNull();
        assertThat(cardData.accountId()).isEqualTo(accountId);
        assertThat(cardData.status()).isEqualTo("ACTIVE");
        assertThat(cardData.maskedPan()).endsWith("1234");
        assertThat(cardData.cardToken()).startsWith("tok_");
        UUID cardId = cardData.id();

        // 3. GET /api/v1/cards/{cardId} -> Verify retrieved card state
        ResponseEntity<ApiResponse<CardResponse>> getCardResp = restTemplate.exchange(
                baseUrl("/api/v1/cards/" + cardId),
                HttpMethod.GET,
                new HttpEntity<>(createHeaders(null)),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(getCardResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getCardResp.getBody()).isNotNull();
        assertThat(getCardResp.getBody().getData().status()).isEqualTo("ACTIVE");

        // 4. PUT /api/v1/cards/{cardId}/controls -> Configure channel permissions and spend limits
        UpdateCardControlsRequest controlsReq = new UpdateCardControlsRequest(
                50000.0,
                10000.0,
                true,
                false,
                false
        );
        HttpEntity<UpdateCardControlsRequest> controlsHttpEntity = new HttpEntity<>(controlsReq, createHeaders(null));

        ResponseEntity<ApiResponse<CardControlsResponse>> controlsResp = restTemplate.exchange(
                baseUrl("/api/v1/cards/" + cardId + "/controls"),
                HttpMethod.PUT,
                controlsHttpEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(controlsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controlsResp.getBody()).isNotNull();
        assertThat(controlsResp.getBody().getData().onlineEnabled()).isTrue();
        assertThat(controlsResp.getBody().getData().atmEnabled()).isFalse();

        // 5. PATCH /api/v1/cards/{cardId}/status -> Freeze card
        UpdateCardStatusRequest freezeReq = new UpdateCardStatusRequest("FROZEN");
        HttpEntity<UpdateCardStatusRequest> freezeHttpEntity = new HttpEntity<>(freezeReq, createHeaders(null));

        ResponseEntity<ApiResponse<CardResponse>> freezeResp = restTemplate.exchange(
                baseUrl("/api/v1/cards/" + cardId + "/status"),
                HttpMethod.PATCH,
                freezeHttpEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(freezeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(freezeResp.getBody()).isNotNull();
        assertThat(freezeResp.getBody().getData().status()).isEqualTo("FROZEN");

        // 6. POST /api/v1/transactions/authorize -> Attempt swipe on FROZEN card -> Assert Declined / 422
        AuthorizeTransactionRequest authReqFrozen = new AuthorizeTransactionRequest(
                cardId,
                null,
                BigDecimal.valueOf(50.0),
                "USD",
                "Walmart Supercenter",
                true,
                false,
                false
        );
        String frozenKey = "e2e-idemp-frozen-" + UUID.randomUUID();
        HttpEntity<AuthorizeTransactionRequest> authFrozenEntity = new HttpEntity<>(authReqFrozen, createHeaders(frozenKey));

        ResponseEntity<ApiResponse<AuthorizeTransactionRequest.Response>> authFrozenResp = restTemplate.exchange(
                baseUrl("/api/v1/transactions/authorize"),
                HttpMethod.POST,
                authFrozenEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(authFrozenResp.getStatusCode().value()).isIn(400, 422);
        assertThat(authFrozenResp.getBody()).isNotNull();
        assertThat(authFrozenResp.getBody().isSuccess()).isFalse();
        assertThat(authFrozenResp.getBody().getError().getMessage()).containsIgnoringCase("frozen");

        // 7. PATCH /api/v1/cards/{cardId}/status -> Unfreeze / Re-activate card
        UpdateCardStatusRequest activeReq = new UpdateCardStatusRequest("ACTIVE");
        HttpEntity<UpdateCardStatusRequest> activeHttpEntity = new HttpEntity<>(activeReq, createHeaders(null));

        ResponseEntity<ApiResponse<CardResponse>> unfreezeResp = restTemplate.exchange(
                baseUrl("/api/v1/cards/" + cardId + "/status"),
                HttpMethod.PATCH,
                activeHttpEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(unfreezeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unfreezeResp.getBody()).isNotNull();
        assertThat(unfreezeResp.getBody().getData().status()).isEqualTo("ACTIVE");

        // 8. POST /api/v1/transactions/authorize -> Attempt swipe on re-activated card -> Assert 200 APPROVED
        AuthorizeTransactionRequest authReqActive = new AuthorizeTransactionRequest(
                cardId,
                null,
                BigDecimal.valueOf(50.0),
                "USD",
                "Walmart Supercenter",
                true,
                false,
                false
        );
        String activeKey = "e2e-idemp-active-" + UUID.randomUUID();
        HttpEntity<AuthorizeTransactionRequest> authActiveEntity = new HttpEntity<>(authReqActive, createHeaders(activeKey));

        ResponseEntity<ApiResponse<AuthorizeTransactionRequest.Response>> authActiveResp = restTemplate.exchange(
                baseUrl("/api/v1/transactions/authorize"),
                HttpMethod.POST,
                authActiveEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(authActiveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authActiveResp.getBody()).isNotNull();
        assertThat(authActiveResp.getBody().isSuccess()).isTrue();
        assertThat(authActiveResp.getBody().getData().status()).isEqualTo("AUTHORIZED");
        assertThat(authActiveResp.getBody().getData().authorizationId()).isNotNull();

        // 9. PATCH /api/v1/cards/{cardId}/status -> Terminate card
        UpdateCardStatusRequest termReq = new UpdateCardStatusRequest("TERMINATED");
        HttpEntity<UpdateCardStatusRequest> termHttpEntity = new HttpEntity<>(termReq, createHeaders(null));

        ResponseEntity<ApiResponse<CardResponse>> termResp = restTemplate.exchange(
                baseUrl("/api/v1/cards/" + cardId + "/status"),
                HttpMethod.PATCH,
                termHttpEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(termResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(termResp.getBody()).isNotNull();
        assertThat(termResp.getBody().getData().status()).isEqualTo("TERMINATED");

        // 10. Attempt to reactivate a TERMINATED card -> Must fail
        ResponseEntity<ApiResponse<CardResponse>> reactivateResp = restTemplate.exchange(
                baseUrl("/api/v1/cards/" + cardId + "/status"),
                HttpMethod.PATCH,
                activeHttpEntity,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(reactivateResp.getStatusCode().value()).isIn(400, 422);
        assertThat(reactivateResp.getBody()).isNotNull();
        assertThat(reactivateResp.getBody().isSuccess()).isFalse();
    }
}
