package com.cardplatform.api.controller;

import com.cardplatform.api.dto.ApiResponse;
import com.cardplatform.api.dto.PostJournalEntryRequest;
import com.cardplatform.application.ledger.LedgerPostingService;
import com.cardplatform.application.ledger.RecordJournalEntryUseCase;
import com.cardplatform.common.audit.TraceContext;
import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.ledger.model.AccountBalances;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.LedgerPosting;
import com.cardplatform.domain.ledger.model.PostingType;
import com.cardplatform.domain.ledger.repository.LedgerRepository;
import com.cardplatform.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = OpenApiConfig.TAG_LEDGER, description = "Immutable multi-legged balanced journal entries, compensating reversals, and account balance inspection.")
public class LedgerController {

    private final LedgerPostingService ledgerPostingService;
    private final LedgerRepository ledgerRepository;

    public LedgerController(LedgerPostingService ledgerPostingService, LedgerRepository ledgerRepository) {
        this.ledgerPostingService = ledgerPostingService;
        this.ledgerRepository = ledgerRepository;
    }

    @PostMapping("/entries")
    @Operation(summary = "Post balanced double-entry journal entry",
            description = "Atomically records an immutable multi-legged journal entry. Validates that sum(Debits) == sum(Credits), updates real-time account balances, and writes an event to the Transactional Outbox.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Journal entry successfully committed",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payload or unbalanced debits/credits",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "One or more target accounts not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Ledger invariant failure or currency mismatch",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PostJournalEntryRequest.Response>> postEntry(
            @Parameter(description = "Idempotency key to guarantee single journal execution", example = "idemp_ledger_post_001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody PostJournalEntryRequest request) {

        String idempotencyKey = idempotencyKeyHeader != null ? idempotencyKeyHeader
                : (request.idempotencyKey() != null ? request.idempotencyKey() : TraceContext.getIdempotencyKey().orElse(null));

        String correlationId = request.correlationId() != null ? request.correlationId()
                : TraceContext.getOrCreateCorrelationId();

        UUID entryId = request.transactionRefId() != null ? request.transactionRefId() : UUID.randomUUID();

        List<RecordJournalEntryUseCase.PostingLegCommand> legs = request.postings().stream()
                .map(p -> new RecordJournalEntryUseCase.PostingLegCommand(
                        p.accountId(),
                        PostingType.valueOf(p.entryType().toUpperCase()),
                        MonetaryAmount.of(p.amount(), p.currency()),
                        p.description()
                ))
                .toList();

        RecordJournalEntryUseCase.RecordJournalEntryCommand command =
                new RecordJournalEntryUseCase.RecordJournalEntryCommand(
                        entryId,
                        idempotencyKey,
                        correlationId,
                        request.description() != null ? request.description() : "Ledger posting",
                        legs
                );

        JournalEntry journalEntry = ledgerPostingService.recordJournalEntry(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(PostJournalEntryRequest.Response.fromDomain(journalEntry)));
    }

    @GetMapping("/entries/{entryId}")
    @Operation(summary = "Get journal entry by UUID", description = "Fetches complete immutable journal entry details including all debit/credit posting legs.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Journal entry retrieved",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Journal entry not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PostJournalEntryRequest.Response>> getEntry(
            @Parameter(description = "Journal Entry UUID", required = true, example = "2b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb44")
            @PathVariable UUID entryId) {
        JournalEntry journalEntry = ledgerPostingService.getJournalEntry(entryId);
        return ResponseEntity.ok(ApiResponse.success(PostJournalEntryRequest.Response.fromDomain(journalEntry)));
    }

    @GetMapping("/entries/correlation/{correlationId}")
    @Operation(summary = "Get journal entry by correlation ID", description = "Fetches journal entry by distributed tracing correlation ID.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Journal entry retrieved",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Journal entry not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PostJournalEntryRequest.Response>> getEntryByCorrelation(
            @Parameter(description = "Correlation ID", required = true, example = "corr_trace_98765")
            @PathVariable String correlationId) {
        JournalEntry journalEntry = ledgerRepository.findByCorrelationId(correlationId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found with correlation ID: " + correlationId));
        return ResponseEntity.ok(ApiResponse.success(PostJournalEntryRequest.Response.fromDomain(journalEntry)));
    }

    @PostMapping("/entries/{entryId}/reversal")
    @Operation(summary = "Create compensating reversal journal entry",
            description = "Creates a strictly balanced compensating inverse journal entry reversing all debits/credits without altering previous immutable ledger history.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Compensating reversal posted",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Original journal entry not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PostJournalEntryRequest.Response>> createReversal(
            @Parameter(description = "Original Journal Entry UUID to reverse", required = true, example = "2b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb44")
            @PathVariable UUID entryId,
            @Parameter(description = "Reason for reversal", example = "Customer dispute refund")
            @RequestParam(defaultValue = "Manual reversal") String reason) {
        JournalEntry reversal = ledgerPostingService.createCompensatingReversal(entryId, reason);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(PostJournalEntryRequest.Response.fromDomain(reversal)));
    }

    @GetMapping("/accounts/{accountId}/balances")
    @Operation(summary = "Query real-time account balances",
            description = "Returns current settled ledger balance, active pending holds balance, and available free-to-spend balance.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account balances retrieved",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AccountBalances>> getBalances(
            @Parameter(description = "Account UUID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID accountId) {
        AccountBalances balances = ledgerPostingService.getAccountBalances(accountId);
        return ResponseEntity.ok(ApiResponse.success(balances));
    }

    @GetMapping("/accounts/{accountId}/postings")
    @Operation(summary = "Query account ledger posting history", description = "Returns chronological list of all debit and credit ledger legs for an account.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Postings history retrieved",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<List<PostJournalEntryRequest.PostingResponse>>> getPostings(
            @Parameter(description = "Account UUID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID accountId) {
        List<LedgerPosting> postings = ledgerRepository.findPostingsByAccountId(accountId);
        List<PostJournalEntryRequest.PostingResponse> responses = postings.stream()
                .map(PostJournalEntryRequest.PostingResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
