package com.cardplatform.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Validation failure description on a specific request property")
public class ValidationErrorDetail {

    @Schema(description = "Request property / field name", example = "amount")
    private final String field;

    @Schema(description = "Validation failure description", example = "amount must be greater than 0")
    private final String message;

    @Schema(description = "Value rejected during validation", example = "-50.00")
    private final Object rejectedValue;

    @com.fasterxml.jackson.annotation.JsonCreator
    public ValidationErrorDetail(
            @com.fasterxml.jackson.annotation.JsonProperty("field") String field,
            @com.fasterxml.jackson.annotation.JsonProperty("message") String message,
            @com.fasterxml.jackson.annotation.JsonProperty("rejectedValue") Object rejectedValue) {
        this.field = field;
        this.message = message;
        this.rejectedValue = rejectedValue;
    }

    public static ValidationErrorDetail of(String field, String message, Object rejectedValue) {
        return new ValidationErrorDetail(field, message, rejectedValue);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    public static class Builder {
        private String field;
        private String message;
        private Object rejectedValue;

        public Builder field(String field) {
            this.field = field;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder rejectedValue(Object rejectedValue) {
            this.rejectedValue = rejectedValue;
            return this;
        }

        public ValidationErrorDetail build() {
            return new ValidationErrorDetail(field, message, rejectedValue);
        }
    }
}
