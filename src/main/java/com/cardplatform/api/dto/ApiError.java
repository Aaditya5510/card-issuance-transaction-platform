package com.cardplatform.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard FinTech API error structure")
public class ApiError {

    @Schema(description = "Machine-readable error code", example = "INSUFFICIENT_FUNDS")
    private final String code;

    @Schema(description = "Human-readable error description", example = "Insufficient available balance for authorization hold")
    private final String message;

    @Schema(description = "Field-level validation error breakdown, if applicable")
    private final List<ValidationErrorDetail> details;

    @Schema(description = "UTC ISO timestamp when error was captured", example = "2026-09-09T10:15:30.000Z")
    private final Instant timestamp;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ApiError(
            @com.fasterxml.jackson.annotation.JsonProperty("code") String code,
            @com.fasterxml.jackson.annotation.JsonProperty("message") String message,
            @com.fasterxml.jackson.annotation.JsonProperty("details") List<ValidationErrorDetail> details,
            @com.fasterxml.jackson.annotation.JsonProperty("timestamp") Instant timestamp) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, Instant.now());
    }

    public static ApiError of(String code, String message, List<ValidationErrorDetail> details) {
        return new ApiError(code, message, details, Instant.now());
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<ValidationErrorDetail> getDetails() {
        return details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public static class Builder {
        private String code;
        private String message;
        private List<ValidationErrorDetail> details;
        private Instant timestamp;

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder details(List<ValidationErrorDetail> details) {
            this.details = details;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ApiError build() {
            return new ApiError(code, message, details, timestamp);
        }
    }
}
