package com.cardplatform.infrastructure.idempotency.filter;

import com.cardplatform.api.dto.ApiError;
import com.cardplatform.api.dto.ApiResponse;
import com.cardplatform.common.audit.TraceContext;
import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.common.exception.IdempotencyInProgressException;
import com.cardplatform.common.exception.IdempotencyPayloadMismatchException;
import com.cardplatform.infrastructure.idempotency.IdempotencyRecord;
import com.cardplatform.infrastructure.idempotency.IdempotencyService;
import com.cardplatform.infrastructure.security.SecurityKeyHashingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * Enterprise-grade HTTP Idempotency Filter enforcing IETF standards and atomic response caching.
 * Applies to state-mutating transaction endpoints to prevent duplicate charges and race conditions.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper != null ? objectMapper.copy().findAndRegisterModules() : new ObjectMapper().findAndRegisterModules();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Only filter state-mutating methods on transaction endpoints
        boolean isMutating = MUTATING_METHODS.contains(method.toUpperCase());
        boolean isTransactionPath = path.startsWith("/api/v1/transactions") ||
                path.startsWith("/v1/transactions") ||
                path.contains("/transactions");

        return !isMutating || !isTransactionPath;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String idempotencyKey = extractIdempotencyKey(request);

        // 1. Enforce header contract on state-mutating transaction endpoints
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Missing required Idempotency-Key header on mutating endpoint: {} {}",
                    request.getMethod(), request.getRequestURI());
            writeErrorResponse(
                    response,
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.IDEMPOTENCY_KEY_MISSING.getValue(),
                    "Idempotency-Key header is required for state-mutating payment transactions"
            );
            return;
        }

        TraceContext.setIdempotencyKey(idempotencyKey);
        response.setHeader(IDEMPOTENCY_HEADER, idempotencyKey);

        // 2. Wrap request to allow repeatable reads and response to cache output
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        // 3. Compute SHA-256 fingerprint of request payload
        String rawBody = cachedRequest.getBodyAsString();
        String requestHash = SecurityKeyHashingUtil.sha256Hex(rawBody);

        // 4. Check or atomically reserve key
        Optional<IdempotencyRecord> existingRecordOpt;
        try {
            existingRecordOpt = idempotencyService.checkOrReserveWithHash(idempotencyKey, requestHash, null);
        } catch (IdempotencyPayloadMismatchException ex) {
            log.warn("Idempotency payload mismatch for key '{}'", idempotencyKey);
            writeErrorResponse(
                    response,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ErrorCode.IDEMPOTENCY_CONFLICT.getValue(),
                    ex.getMessage()
            );
            return;
        } catch (IdempotencyInProgressException ex) {
            log.warn("Concurrent in-progress request rejected for key '{}'", idempotencyKey);
            writeErrorResponse(
                    response,
                    HttpStatus.CONFLICT,
                    ErrorCode.IDEMPOTENCY_PROCESSING.getValue(),
                    ex.getMessage()
            );
            return;
        }

        // 5. Replay cached response if RESOLVED
        if (existingRecordOpt.isPresent()) {
            IdempotencyRecord record = existingRecordOpt.get();
            log.info("Replaying cached idempotent response for key '{}', status={}",
                    idempotencyKey, record.responseCode());
            replayCachedResponse(response, record);
            return;
        }

        // 6. Execute downstream chain for newly reserved key
        try {
            filterChain.doFilter(cachedRequest, responseWrapper);

            int status = responseWrapper.getStatus();
            byte[] responseBytes = responseWrapper.getContentAsByteArray();
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);

            // Capture and resolve idempotent response on success or business/client error
            if (status < 500) {
                idempotencyService.resolve(idempotencyKey, status, responseBody);
            } else {
                idempotencyService.markFailed(idempotencyKey);
            }

            responseWrapper.copyBodyToResponse();
        } catch (Throwable ex) {
            log.error("Downstream exception during idempotent execution for key '{}'", idempotencyKey, ex);
            idempotencyService.markFailed(idempotencyKey);
            throw ex;
        }
    }

    private String extractIdempotencyKey(HttpServletRequest request) {
        String key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            key = request.getHeader("idempotency-key");
        }
        if (key == null || key.isBlank()) {
            key = request.getHeader("X-Idempotency-Key");
        }
        return key != null ? key.trim() : null;
    }

    private void replayCachedResponse(HttpServletResponse response, IdempotencyRecord record) throws IOException {
        int status = record.responseCode() != null ? record.responseCode() : HttpStatus.OK.value();
        response.setStatus(status);
        response.setHeader(IDEMPOTENCY_HEADER, record.idempotencyKey());
        response.setHeader(IDEMPOTENT_REPLAYED_HEADER, "true");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String body = record.responseBody() != null ? record.responseBody() : "";
        response.getWriter().write(body);
        response.flushBuffer();
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            HttpStatus status,
            String errorCode,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiError apiError = ApiError.of(errorCode, message);
        ApiResponse<Void> apiResponse = ApiResponse.error(apiError);

        String json = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(json);
        response.flushBuffer();
    }
}
