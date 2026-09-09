package com.cardplatform.common.audit;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local context and SLF4J MDC helper for distributed correlation, audit trails, and PCI compliance.
 */
public final class TraceContext {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String ACCOUNT_ID_KEY = "accountId";

    private TraceContext() {
    }

    public static String getOrCreateCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
        return correlationId;
    }

    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        } else {
            getOrCreateCorrelationId();
        }
    }

    public static Optional<String> getCorrelationId() {
        return Optional.ofNullable(MDC.get(CORRELATION_ID_KEY));
    }

    public static void setIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            MDC.put(IDEMPOTENCY_KEY, idempotencyKey);
        }
    }

    public static Optional<String> getIdempotencyKey() {
        return Optional.ofNullable(MDC.get(IDEMPOTENCY_KEY));
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(IDEMPOTENCY_KEY);
        MDC.remove(ACCOUNT_ID_KEY);
    }
}
