package com.cardplatform.common.exception;

import com.cardplatform.api.dto.ApiError;
import com.cardplatform.api.dto.ApiResponse;
import com.cardplatform.api.dto.ValidationErrorDetail;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException ex) {
        log.warn("Business/Platform exception: status={}, code={}, message={}",
                ex.getStatus(), ex.getErrorCode().getValue(), ex.getMessage());

        ApiError error = ApiError.of(ex.getErrorCode().getValue(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(error));
    }

    @ExceptionHandler(LedgerImbalanceException.class)
    public ResponseEntity<ApiResponse<Void>> handleLedgerImbalanceException(LedgerImbalanceException ex) {
        log.error("Ledger imbalance invariant failed: status={}, code={}, message={}",
                ex.getStatus(), ex.getErrorCode().getValue(), ex.getMessage());

        ApiError error = ApiError.of(ex.getErrorCode().getValue(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(error));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex) {
        log.warn("Domain invariant exception: status={}, code={}, message={}",
                ex.getStatus(), ex.getErrorCode().getValue(), ex.getMessage());

        ApiError error = ApiError.of(ex.getErrorCode().getValue(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Validation error on method argument: {}", ex.getMessage());

        List<ValidationErrorDetail> validationErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .toList();

        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR.getValue(),
                "Validation failed for one or more fields",
                validationErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());

        List<ValidationErrorDetail> validationErrors = ex.getConstraintViolations()
                .stream()
                .map(violation -> ValidationErrorDetail.of(
                        violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        violation.getInvalidValue()
                ))
                .toList();

        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_ERROR.getValue(),
                "Constraint violation occurred",
                validationErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON or unreadable HTTP message: {}", ex.getMessage());

        ApiError error = ApiError.of(
                ErrorCode.MALFORMED_REQUEST.getValue(),
                "Malformed request body or invalid format"
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());

        ApiError error = ApiError.of(
                ErrorCode.BAD_REQUEST.getValue(),
                "Required parameter is missing: " + ex.getParameterName()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter: {}", ex.getName());

        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valid type";
        ApiError error = ApiError.of(
                ErrorCode.BAD_REQUEST.getValue(),
                "Parameter '" + ex.getName() + "' should be of type " + expectedType
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("HTTP method not supported: {}", ex.getMethod());

        ApiError error = ApiError.of(
                ErrorCode.BAD_REQUEST.getValue(),
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint"
        );

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(error));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Static or endpoint resource not found: {}", ex.getResourcePath());

        ApiError error = ApiError.of(
                ErrorCode.RESOURCE_NOT_FOUND.getValue(),
                "Endpoint resource not found: /" + ex.getResourcePath()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception caught: ", ex);

        ApiError error = ApiError.of(
                ErrorCode.INTERNAL_SERVER_ERROR.getValue(),
                "An unexpected internal server error occurred"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(error));
    }

    private ValidationErrorDetail mapFieldError(FieldError fieldError) {
        return ValidationErrorDetail.of(
                fieldError.getField(),
                fieldError.getDefaultMessage(),
                fieldError.getRejectedValue()
        );
    }
}
