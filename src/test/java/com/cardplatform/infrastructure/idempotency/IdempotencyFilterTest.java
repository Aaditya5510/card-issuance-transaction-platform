package com.cardplatform.infrastructure.idempotency;

import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.infrastructure.idempotency.filter.IdempotencyFilter;
import com.cardplatform.infrastructure.persistence.entity.IdempotencyRecordJpaEntity;
import com.cardplatform.infrastructure.security.SecurityKeyHashingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock
    private SpringDataIdempotencyRepository repository;

    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private IdempotencyService idempotencyService;
    private IdempotencyFilter idempotencyFilter;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(repository);
        idempotencyFilter = new IdempotencyFilter(idempotencyService, objectMapper);
    }

    @Test
    @DisplayName("Should process new request, execute filter chain, and resolve cached response")
    void shouldProcessNewRequestSuccessfully() throws ServletException, IOException {
        String key = "idemp_new_123";
        String requestPayload = "{\"cardId\":\"" + UUID.randomUUID() + "\",\"amount\":1500,\"currency\":\"INR\"}";
        String responsePayload = "{\"status\":\"APPROVED\",\"authCode\":\"AUTH-999\"}";

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions/authorize");
        request.addHeader("Idempotency-Key", key);
        request.setContent(requestPayload.getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(repository.save(any(IdempotencyRecordJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        doAnswer(invocation -> {
            jakarta.servlet.http.HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.OK.value());
            res.getWriter().write(responsePayload);
            return null;
        }).when(filterChain).doFilter(any(), any());

        idempotencyFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader("Idempotency-Key")).isEqualTo(key);
        assertThat(response.getHeader("Idempotent-Replayed")).isNull();
    }

    @Test
    @DisplayName("Should replay cached response with Idempotent-Replayed: true without invoking filter chain")
    void shouldReplayCachedResponseWithoutInvokingFilterChain() throws ServletException, IOException {
        String key = "idemp_replay_456";
        String requestPayload = "{\"cardId\":\"" + UUID.randomUUID() + "\",\"amount\":2500,\"currency\":\"INR\"}";
        String cachedResponsePayload = "{\"status\":\"APPROVED\",\"authCode\":\"AUTH-CACHED\"}";

        IdempotencyRecordJpaEntity existing = new IdempotencyRecordJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(key);
        existing.setRequestHash(SecurityKeyHashingUtil.sha256Hex(requestPayload));
        existing.setStatus(IdempotencyStatus.RESOLVED.name());
        existing.setResponseCode(200);
        existing.setResponseBody(cachedResponsePayload);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions/authorize");
        request.addHeader("Idempotency-Key", key);
        request.setContent(requestPayload.getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        idempotencyFilter.doFilter(request, response, filterChain);

        // Verify downstream filter chain was completely short-circuited (0 extra transactions/debits)
        verify(filterChain, never()).doFilter(any(), any());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader("Idempotency-Key")).isEqualTo(key);
        assertThat(response.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(response.getContentAsString()).isEqualTo(cachedResponsePayload);
    }

    @Test
    @DisplayName("Should reject with HTTP 409 Conflict when duplicate in-flight request arrives")
    void shouldRejectWithConflictWhenDuplicateInProgress() throws ServletException, IOException {
        String key = "idemp_inflight_789";
        String requestPayload = "{\"amount\":500}";

        IdempotencyRecordJpaEntity existing = new IdempotencyRecordJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(key);
        existing.setRequestHash(SecurityKeyHashingUtil.sha256Hex(requestPayload));
        existing.setStatus(IdempotencyStatus.IN_PROGRESS.name());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions/authorize");
        request.addHeader("Idempotency-Key", key);
        request.setContent(requestPayload.getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        idempotencyFilter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getContentAsString()).contains(ErrorCode.IDEMPOTENCY_PROCESSING.getValue());
    }

    @Test
    @DisplayName("Should reject with HTTP 422 Unprocessable Entity when idempotency key is reused with different payload")
    void shouldRejectWithUnprocessableEntityWhenPayloadMismatch() throws ServletException, IOException {
        String key = "idemp_mismatch_000";
        String originalPayload = "{\"amount\":1000}";
        String differentPayload = "{\"amount\":9999}";

        IdempotencyRecordJpaEntity existing = new IdempotencyRecordJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(key);
        existing.setRequestHash(SecurityKeyHashingUtil.sha256Hex(originalPayload));
        existing.setStatus(IdempotencyStatus.RESOLVED.name());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions/authorize");
        request.addHeader("Idempotency-Key", key);
        request.setContent(differentPayload.getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        idempotencyFilter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(response.getContentAsString()).contains(ErrorCode.IDEMPOTENCY_CONFLICT.getValue());
    }

    @Test
    @DisplayName("Should reject with HTTP 400 Bad Request when Idempotency-Key header is missing on mutating payment endpoint")
    void shouldRejectWithBadRequestWhenHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions/authorize");
        request.setContent("{\"amount\":1000}".getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();

        idempotencyFilter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentAsString()).contains(ErrorCode.IDEMPOTENCY_KEY_MISSING.getValue());
    }

    @Test
    @DisplayName("Should bypass idempotency checks on GET or non-transaction endpoints")
    void shouldBypassOnNonMutatingEndpoint() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cards/card-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        idempotencyFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(repository, never()).findByIdempotencyKey(any());
    }
}
