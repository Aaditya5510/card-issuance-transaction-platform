package com.cardplatform.api.controller;

import com.cardplatform.api.dto.ApiResponse;
import com.cardplatform.infrastructure.config.OpenApiConfig;
import com.cardplatform.infrastructure.outbox.service.OutboxEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = OpenApiConfig.TAG_SYSTEM_HEALTH, description = "Health checks, outbox publisher status, and operational metrics.")
public class SystemController {

    private final OutboxEventPublisher outboxEventPublisher;

    public SystemController(@Autowired(required = false) OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @GetMapping("/health")
    @Operation(summary = "Platform health check", description = "Returns the operational status, version, and server timestamp.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Platform is operational",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "card-platform",
                "version", "v1.0.0",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.success(health));
    }

    @GetMapping("/outbox/status")
    @Operation(summary = "Transactional Outbox poller status", description = "Returns configuration and status of the asynchronous outbox publisher worker.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Outbox publisher status retrieved",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOutboxStatus() {
        int maxRetries = outboxEventPublisher != null ? outboxEventPublisher.getMaxRetries() : 5;
        int batchSize = outboxEventPublisher != null ? outboxEventPublisher.getBatchSize() : 50;
        int backoff = outboxEventPublisher != null ? outboxEventPublisher.getBaseBackoffSeconds() : 2;

        Map<String, Object> status = Map.of(
                "status", "ACTIVE",
                "maxRetries", maxRetries,
                "batchSize", batchSize,
                "baseBackoffSeconds", backoff,
                "dlqThreshold", maxRetries
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
