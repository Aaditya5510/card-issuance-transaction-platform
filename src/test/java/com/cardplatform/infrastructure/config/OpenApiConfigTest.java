package com.cardplatform.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    @DisplayName("Should initialize enterprise OpenAPI 3 definition with tags, headers, and metadata")
    void shouldConfigureOpenApiProperly() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.customOpenAPI();

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Card Issuance & Real-Time Transaction Authorization Platform API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1.0.0");
        assertThat(openAPI.getInfo().getDescription()).contains("Production-grade, high-throughput card issuing");

        // Verify tags
        assertThat(openAPI.getTags()).extracting("name").containsExactlyInAnyOrder(
                OpenApiConfig.TAG_CARD_MANAGEMENT,
                OpenApiConfig.TAG_TRANSACTION_AUTH,
                OpenApiConfig.TAG_LEDGER,
                OpenApiConfig.TAG_SYSTEM_HEALTH
        );

        // Verify servers
        assertThat(openAPI.getServers()).isNotEmpty();

        // Verify components & headers
        assertThat(openAPI.getComponents().getParameters()).containsKey("Idempotency-Key");
        assertThat(openAPI.getComponents().getHeaders()).containsKey("Idempotent-Replayed");
    }
}
