package com.cardplatform.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Payload for issuing a new payment card attached to a funding account")
public record IssueCardRequest(
        @Schema(description = "Target funding account UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "accountId is required")
        UUID accountId,

        @Schema(description = "Primary Account Number (PAN) - 16 digits", example = "4111111111114242")
        @NotBlank(message = "pan is required")
        String pan
) {}
