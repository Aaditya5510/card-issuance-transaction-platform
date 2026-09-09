package com.cardplatform.api.controller;

import com.cardplatform.api.dto.ApiResponse;
import com.cardplatform.api.dto.CardControlsResponse;
import com.cardplatform.api.dto.CardResponse;
import com.cardplatform.api.dto.IssueCardRequest;
import com.cardplatform.api.dto.UpdateCardControlsRequest;
import com.cardplatform.api.dto.UpdateCardStatusRequest;
import com.cardplatform.application.card.IssueCardUseCase;
import com.cardplatform.application.card.UpdateCardControlsUseCase;
import com.cardplatform.application.card.UpdateCardStatusUseCase;
import com.cardplatform.common.exception.ResourceNotFoundException;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.card.model.Card;
import com.cardplatform.domain.card.model.CardControls;
import com.cardplatform.domain.card.model.CardStatus;
import com.cardplatform.domain.card.repository.CardRepository;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = OpenApiConfig.TAG_CARD_MANAGEMENT, description = "Card issuance, lifecycle state transitions (FREEZE, ACTIVATE, TERMINATE), and spending controls.")
public class CardController {

    private final IssueCardUseCase issueCardUseCase;
    private final UpdateCardStatusUseCase updateCardStatusUseCase;
    private final UpdateCardControlsUseCase updateCardControlsUseCase;
    private final CardRepository cardRepository;

    public CardController(
            IssueCardUseCase issueCardUseCase,
            UpdateCardStatusUseCase updateCardStatusUseCase,
            UpdateCardControlsUseCase updateCardControlsUseCase,
            CardRepository cardRepository) {
        this.issueCardUseCase = issueCardUseCase;
        this.updateCardStatusUseCase = updateCardStatusUseCase;
        this.updateCardControlsUseCase = updateCardControlsUseCase;
        this.cardRepository = cardRepository;
    }

    @PostMapping
    @Operation(summary = "Issue a new payment card", description = "Provisions a new virtual/physical payment card linked to an active funding account.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Card successfully issued",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid card parameters or invalid PAN",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Target account not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Business rule violation (e.g. account closed)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<CardResponse>> issueCard(@Valid @RequestBody IssueCardRequest request) {
        Card card = issueCardUseCase.execute(request.accountId(), request.pan());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(CardResponse.fromDomain(card)));
    }

    @GetMapping("/{cardId}")
    @Operation(summary = "Retrieve card details", description = "Fetches card details including masked PAN and current status by card UUID.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Card retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Card not found with provided ID",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<CardResponse>> getCard(
            @Parameter(description = "Card unique identifier UUID", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            @PathVariable UUID cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with ID: " + cardId));
        return ResponseEntity.ok(ApiResponse.success(CardResponse.fromDomain(card)));
    }

    @PatchMapping("/{cardId}/status")
    @Operation(summary = "Update card lifecycle status", description = "Transitions card state across ACTIVE, FROZEN, or TERMINATED lifecycle states.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Card status updated successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status value supplied",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Card not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Illegal status state transition (e.g. from TERMINATED)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<CardResponse>> updateStatus(
            @Parameter(description = "Card unique identifier UUID", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateCardStatusRequest request) {
        CardStatus status = CardStatus.valueOf(request.status().toUpperCase());
        Card updatedCard = updateCardStatusUseCase.execute(cardId, status);
        return ResponseEntity.ok(ApiResponse.success(CardResponse.fromDomain(updatedCard)));
    }

    @PutMapping("/{cardId}/controls")
    @Operation(summary = "Configure card spend controls and channels", description = "Updates daily limits, single transaction limits, and enables/disables online, ATM, or international channels.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Card controls updated successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid limits provided",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Card not found",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<CardControlsResponse>> updateControls(
            @Parameter(description = "Card unique identifier UUID", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            @PathVariable UUID cardId,
            @RequestBody UpdateCardControlsRequest request) {
        MonetaryAmount daily = request.dailyLimit() != null ? MonetaryAmount.of(request.dailyLimit(), "INR") : null;
        MonetaryAmount perTx = request.perTxLimit() != null ? MonetaryAmount.of(request.perTxLimit(), "INR") : null;

        CardControls controls = updateCardControlsUseCase.execute(
                cardId,
                daily,
                perTx,
                request.onlineEnabled(),
                request.atmEnabled(),
                request.internationalEnabled()
        );
        return ResponseEntity.ok(ApiResponse.success(CardControlsResponse.fromDomain(controls)));
    }
}
