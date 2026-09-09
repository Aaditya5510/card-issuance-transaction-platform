package com.cardplatform.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload to update spending thresholds and channel permissions on a card")
public record UpdateCardControlsRequest(
        @Schema(description = "Daily aggregate spend limit in funding currency", example = "50000.00")
        Double dailyLimit,

        @Schema(description = "Single transaction limit in funding currency", example = "10000.00")
        Double perTxLimit,

        @Schema(description = "Enable / Disable Online (CNP) purchases", example = "true")
        Boolean onlineEnabled,

        @Schema(description = "Enable / Disable ATM cash withdrawals", example = "true")
        Boolean atmEnabled,

        @Schema(description = "Enable / Disable International cross-border transactions", example = "false")
        Boolean internationalEnabled
) {}
