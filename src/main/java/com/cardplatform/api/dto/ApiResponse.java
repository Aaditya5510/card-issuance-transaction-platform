package com.cardplatform.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized FinTech API envelope wrapper")
public class ApiResponse<T> {

    @Schema(description = "Operation success indicator", example = "true")
    private final boolean success;

    @Schema(description = "Response payload data")
    private final T data;

    @Schema(description = "Error details payload when success is false")
    private final ApiError error;

    @Schema(description = "UTC ISO timestamp when response was generated", example = "2026-09-09T10:15:30.000Z")
    private final Instant timestamp;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ApiResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("success") boolean success,
            @com.fasterxml.jackson.annotation.JsonProperty("data") T data,
            @com.fasterxml.jackson.annotation.JsonProperty("error") ApiError error,
            @com.fasterxml.jackson.annotation.JsonProperty("timestamp") Instant timestamp) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ApiError getError() {
        return error;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public static class Builder<T> {
        private boolean success;
        private T data;
        private ApiError error;
        private Instant timestamp;

        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> error(ApiError error) {
            this.error = error;
            return this;
        }

        public Builder<T> timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ApiResponse<T> build() {
            return new ApiResponse<>(success, data, error, timestamp);
        }
    }
}
