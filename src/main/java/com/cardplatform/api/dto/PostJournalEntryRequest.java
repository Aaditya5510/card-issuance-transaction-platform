package com.cardplatform.api.dto;

import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.LedgerPosting;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Multi-legged double-entry ledger journal entry posting request")
public record PostJournalEntryRequest(
        @Schema(description = "External business transaction reference UUID", example = "7e1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb99")
        UUID transactionRefId,

        @Schema(description = "Unique IETF Idempotency Key", example = "idemp_ledger_post_001")
        String idempotencyKey,

        @Schema(description = "Traceability Correlation ID", example = "corr_trace_98765")
        String correlationId,

        @Schema(description = "Human-readable description of the accounting event", example = "Customer Wallet Top-up via ACH")
        String description,

        @Schema(description = "Zero-sum balanced list of debit and credit posting legs")
        @NotEmpty(message = "postings cannot be empty")
        List<@Valid PostingRequest> postings
) {
    @Schema(description = "Individual posting leg (Debit or Credit)")
    public record PostingRequest(
            @Schema(description = "Target Account UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @NotNull(message = "accountId is required")
            UUID accountId,

            @Schema(description = "Posting Type: DEBIT or CREDIT", example = "DEBIT")
            @NotNull(message = "entryType is required (DEBIT, CREDIT)")
            String entryType,

            @Schema(description = "Posting amount (positive value)", example = "250.00")
            @NotNull(message = "amount is required")
            BigDecimal amount,

            @Schema(description = "ISO-4217 Currency Code", example = "USD")
            @NotNull(message = "currency is required")
            String currency,

            @Schema(description = "Posting description", example = "ACH Clearing Debit")
            String description
    ) {}

    @Schema(description = "Immutable Journal Entry Confirmation Response")
    public record Response(
            @Schema(description = "Immutable Journal Entry UUID", example = "2b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb44")
            UUID id,

            @Schema(description = "Associated transaction reference UUID", example = "7e1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb99")
            UUID transactionRefId,

            @Schema(description = "Idempotency Key used for posting", example = "idemp_ledger_post_001")
            String idempotencyKey,

            @Schema(description = "Traceability Correlation ID", example = "corr_trace_98765")
            String correlationId,

            @Schema(description = "Journal description", example = "Customer Wallet Top-up via ACH")
            String description,

            @Schema(description = "List of balanced posting legs")
            List<PostingResponse> postings,

            @Schema(description = "Timestamp when journal entry was posted and committed", example = "2026-09-09T10:15:30.000Z")
            Instant postedAt,

            @Schema(description = "Record creation timestamp", example = "2026-09-09T10:15:30.000Z")
            Instant createdAt
    ) {
        public static Response fromDomain(JournalEntry entry) {
            List<PostingResponse> postingResponses = entry.getPostings().stream()
                    .map(PostingResponse::fromDomain)
                    .toList();
            return new Response(
                    entry.getId(),
                    entry.getTransactionRefId(),
                    entry.getIdempotencyKey(),
                    entry.getCorrelationId(),
                    entry.getDescription(),
                    postingResponses,
                    entry.getPostedAt(),
                    entry.getPostedAt()
            );
        }
    }

    @Schema(description = "Committed ledger posting record")
    public record PostingResponse(
            @Schema(description = "Posting leg unique UUID", example = "1a1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb11")
            UUID id,

            @Schema(description = "Account UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID accountId,

            @Schema(description = "Entry Type: DEBIT or CREDIT", example = "DEBIT")
            String entryType,

            @Schema(description = "Amount", example = "250.0000")
            BigDecimal amount,

            @Schema(description = "Currency Code", example = "USD")
            String currency,

            @Schema(description = "Sequential posting leg index", example = "1")
            Integer sequenceNumber,

            @Schema(description = "Account balance immediately following this posting", example = "1250.0000")
            BigDecimal balanceAfter,

            @Schema(description = "Posting leg description", example = "ACH Clearing Debit")
            String description,

            @Schema(description = "Posting timestamp", example = "2026-09-09T10:15:30.000Z")
            Instant createdAt
    ) {
        public static PostingResponse fromDomain(LedgerPosting posting) {
            return new PostingResponse(
                    posting.getId(),
                    posting.getAccountId(),
                    posting.getType().name(),
                    posting.getAmount().getAmount(),
                    posting.getAmount().getCurrency(),
                    posting.getSequenceNumber(),
                    posting.getBalanceAfter() != null ? posting.getBalanceAfter().getAmount() : null,
                    posting.getDescription(),
                    posting.getCreatedAt()
            );
        }
    }
}
