package com.cardplatform.common.exception;

import com.cardplatform.api.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should return 404 with RESOURCE_NOT_FOUND error envelope when ResourceNotFoundException is thrown")
    void shouldHandleResourceNotFoundException() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is(ErrorCode.RESOURCE_NOT_FOUND.getValue())))
                .andExpect(jsonPath("$.error.message", is("Requested resource was not found")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 400 with BAD_REQUEST error envelope when BadRequestException is thrown")
    void shouldHandleBadRequestException() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is(ErrorCode.BAD_REQUEST.getValue())))
                .andExpect(jsonPath("$.error.message", is("Invalid input provided")))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 409 with CONFLICT error envelope when ConflictException is thrown")
    void shouldHandleConflictException() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is(ErrorCode.CONFLICT.getValue())))
                .andExpect(jsonPath("$.error.message", is("Resource already exists with given parameters")))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 400 with validation details when MethodArgumentNotValidException occurs")
    void shouldHandleValidationErrors() throws Exception {
        String invalidPayload = "{\"name\": \"\", \"amount\": null}";

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is(ErrorCode.VALIDATION_ERROR.getValue())))
                .andExpect(jsonPath("$.error.message", is("Validation failed for one or more fields")))
                .andExpect(jsonPath("$.error.details", hasSize(2)))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 400 with MALFORMED_REQUEST when body is unreadable JSON")
    void shouldHandleMalformedJson() throws Exception {
        String malformedJson = "{invalid-json";

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is(ErrorCode.MALFORMED_REQUEST.getValue())))
                .andExpect(jsonPath("$.error.message", containsString("Malformed request body")))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 500 with INTERNAL_SERVER_ERROR when unexpected exception occurs")
    void shouldHandleGenericException() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is(ErrorCode.INTERNAL_SERVER_ERROR.getValue())))
                .andExpect(jsonPath("$.error.message", is("An unexpected internal server error occurred")))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 200 with standard ApiResponse success envelope for valid endpoint call")
    void shouldReturnSuccessResponse() throws Exception {
        mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", is("Operation successful")))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // --- Dummy controller for exception testing ---
    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/success")
        public ApiResponse<String> success() {
            return ApiResponse.success("Operation successful");
        }

        @GetMapping("/not-found")
        public void notFound() {
            throw new ResourceNotFoundException("Requested resource was not found");
        }

        @GetMapping("/bad-request")
        public void badRequest() {
            throw new BadRequestException("Invalid input provided");
        }

        @GetMapping("/conflict")
        public void conflict() {
            throw new ConflictException("Resource already exists with given parameters");
        }

        @GetMapping("/unexpected-error")
        public void unexpectedError() {
            throw new RuntimeException("Unexpected database glitch");
        }

        @PostMapping("/validate")
        public ApiResponse<String> validate(@Valid @RequestBody TestRequest request) {
            return ApiResponse.success("Valid request");
        }
    }

    static class TestRequest {
        @NotBlank(message = "name is required")
        private String name;

        @NotNull(message = "amount is required")
        private Double amount;

        public TestRequest() {
        }

        public TestRequest(String name, Double amount) {
            this.name = name;
            this.amount = amount;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }
    }
}
