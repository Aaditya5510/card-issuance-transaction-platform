package com.cardplatform.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Enterprise-grade OpenAPI 3 and Swagger UI configuration for the Card Issuance & Transaction Platform.
 */
@Configuration
public class OpenApiConfig {

    public static final String TAG_CARD_MANAGEMENT = "Card Management";
    public static final String TAG_TRANSACTION_AUTH = "Transaction Authorization";
    public static final String TAG_LEDGER = "Double-Entry Ledger";
    public static final String TAG_SYSTEM_HEALTH = "System / Health";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Card Issuance & Real-Time Transaction Authorization Platform API")
                        .description("Production-grade, high-throughput card issuing and double-entry ledger settlement engine.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("FinTech Platform Architecture Team")
                                .email("engineering@cardplatform.internal")
                                .url("https://cardplatform.internal"))
                        .license(new License()
                                .name("Proprietary FinTech License")
                                .url("https://cardplatform.internal/license")))
                .servers(List.of(
                        new Server().url("/").description("Current Server Instance"),
                        new Server().url("https://api.cardplatform.internal").description("Production Gateway"),
                        new Server().url("https://sandbox.cardplatform.internal").description("Sandbox Environment")
                ))
                .tags(List.of(
                        new Tag().name(TAG_CARD_MANAGEMENT).description("Card issuance, status lifecycle (FREEZE, ACTIVATE, TERMINATE), and spending controls."),
                        new Tag().name(TAG_TRANSACTION_AUTH).description("Real-time two-phase authorization holds, capture settlement, and voids."),
                        new Tag().name(TAG_LEDGER).description("Immutable multi-legged balanced journal entries and account balance queries."),
                        new Tag().name(TAG_SYSTEM_HEALTH).description("Health checks, outbox publisher status, and operational metrics.")
                ))
                .components(new Components()
                        .addParameters("Idempotency-Key", new HeaderParameter()
                                .name("Idempotency-Key")
                                .description("Unique client-generated IETF idempotency UUID/string to prevent duplicate financial mutations.")
                                .required(true)
                                .schema(new StringSchema().example("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")))
                        .addHeaders("Idempotent-Replayed", new Header()
                                .description("Indicates that the response was served from the idempotent replay cache.")
                                .schema(new StringSchema().example("true")))
                );
    }
}
