package com.cardplatform.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload to update a card's lifecycle status")
public record UpdateCardStatusRequest(
        @Schema(description = "Target lifecycle status: ACTIVE, FROZEN, TERMINATED", example = "FROZEN")
        @NotBlank(message = "status is required")
        String status
) {}
