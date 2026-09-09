package com.cardplatform.api.controller;

import com.cardplatform.api.dto.ApiResponse;
import com.cardplatform.api.dto.AuthorizeTransactionRequest;
import com.cardplatform.application.transaction.AuthorizationEngine;
import com.cardplatform.application.transaction.SettlementUseCase;
import com.cardplatform.common.audit.TraceContext;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.transaction.model.AuthorizationHold;
import com.cardplatform.domain.transaction.model.AuthorizationRequest;
import com.cardplatform.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = OpenApiConfig.TAG_TRANSACTION_AUTH, description = "Real-time two-phase authorization holds, capture settlement, and voids.")
public class TransactionController {

    private final AuthorizationEngine authorizationEngine;
    private final SettlementUseCase settlementUseCase;

    public TransactionController(AuthorizationEngine authorizationEngine, SettlementUseCase settlementUseCase) {
        this.authorizationEngine = authorizationEngine;
        this.settlementUseCase = settlementUseCase;
    }

    @PostMapping("/authorize")
    @Operation(summary = "Real-time Card Authorization (Phase 1)",
            description = "Performs strict card control validations, executes pessimistic write locking against the account, places a 7-day balance hold, and persists an outbox event.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction successfully authorized and hold placed",
                    headers = @Header(name = "Idempotent-Replayed", description = "Set to true if response was replayed from cache", schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or missing parameters",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Card or Account not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Concurrent in-flight request on same Idempotency-Key",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Declined: Insufficient funds, frozen card, or limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AuthorizeTransactionRequest.Response>> authorize(
            @Parameter(description = "IETF standard idempotency UUID/string to guarantee single execution under network retries",
                    example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody AuthorizeTransactionRequest request) {

        String idempotencyKey = idempotencyKeyHeader != null ? idempotencyKeyHeader
                : TraceContext.getIdempotencyKey().orElse(UUID.randomUUID().toString());

        MonetaryAmount amount = MonetaryAmount.of(request.amount(), request.currency());
        AuthorizationRequest authReq = new AuthorizationRequest(
                idempotencyKey,
                request.cardId(),
                request.cardToken(),
                amount,
                request.merchantName(),
                request.online(),
                request.atm(),
                request.international()
        );

        AuthorizationHold hold = authorizationEngine.processAuthorization(authReq);
        return ResponseEntity.ok(ApiResponse.success(AuthorizeTransactionRequest.Response.fromDomain(hold)));
    }

    @PostMapping("/{authorizationId}/settle")
    @Operation(summary = "Capture & Settle Authorization (Phase 2)",
            description = "Resolves the pending authorization hold, settles account balance, marks transaction SETTLED, and posts double-entry settlement journal entries.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authorization successfully captured and settled",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid settlement parameters",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Authorization hold or settlement account not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Hold already released, expired, or settled",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<UUID>> settle(
            @Parameter(description = "Authorization Hold UUID to capture", required = true, example = "7e1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb99")
            @PathVariable UUID authorizationId,
            @Parameter(description = "Merchant clearing settlement account UUID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestParam UUID settlementAccountId) {

        JournalEntry journalEntry = settlementUseCase.settleAuthorization(authorizationId, settlementAccountId);
        return ResponseEntity.ok(ApiResponse.success(journalEntry.getTransactionRefId()));
    }
}
