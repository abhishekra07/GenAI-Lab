package com.genailab.api.exception;

import com.genailab.common.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler — intercepts all exceptions thrown from
 * any controller and maps them to consistent {@link ApiErrorResponse} shapes.
 *
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ModelNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleModelNotFound(
            ModelNotFoundException ex, HttpServletRequest request) {

        log.warn("Model not found: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getErrorCode(), request);
    }

    @ExceptionHandler(ModelNotAvailableException.class)
    public ResponseEntity<ApiErrorResponse> handleModelNotAvailable(
            ModelNotAvailableException ex, HttpServletRequest request) {

        log.warn("Model not available: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex.getErrorCode(), request);
    }

    @ExceptionHandler(ProviderNotAvailableException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderNotAvailable(
            ProviderNotAvailableException ex, HttpServletRequest request) {

        log.warn("Provider not available: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex.getErrorCode(), request);
    }

    @ExceptionHandler(AiRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(
            AiRateLimitException ex, HttpServletRequest request) {

        log.warn("AI rate limit hit: {}", ex.getMessage());
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), ex.getErrorCode(), request);
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderError(
            AiProviderException ex, HttpServletRequest request) {

        // Log as error — this is unexpected, provider returned bad response
        log.error("AI provider error: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex.getErrorCode(), request);
    }

    /**
     * Catch-all for any other AiException subclass we may add in future.
     * Must come AFTER all specific AI exception handlers.
     */
    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiErrorResponse> handleAiException(
            AiException ex, HttpServletRequest request) {

        log.error("Unhandled AI exception: {}", ex.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex.getErrorCode(), request);
    }

    // =========================================================
    // Resource / domain exceptions
    // =========================================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), "RESOURCE_NOT_FOUND", request);
    }

    /**
     * Handles IllegalArgumentException — used throughout services
     * for validation failures (e.g. email already registered,
     * file type not supported, conversation not owned by user).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", request);
    }

    // =========================================================
    // Validation exceptions
    // =========================================================

    /**
     * Handles @Valid annotation failures on request bodies.
     * Collects ALL field errors into one readable message.
     *
     * Example response message:
     * "Validation failed: email must be a valid email address; password must be between 8 and 100 characters"
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed for {}: {}", request.getRequestURI(), message);
        return build(HttpStatus.BAD_REQUEST,
                "Validation failed: " + message, "VALIDATION_ERROR", request);
    }

    /**
     * Handles file uploads that exceed the configured max size.
     * spring.servlet.multipart.max-file-size in application.yml.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleFileTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.warn("File upload too large: {}", ex.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "File exceeds the maximum allowed size of 50MB.",
                "FILE_TOO_LARGE", request);
    }

    // =========================================================
    // Security exceptions
    // =========================================================

    /**
     * Handles requests to protected endpoints without a valid JWT.
     * Spring Security throws this before the request reaches a controller.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed for {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED,
                "Authentication required. Please provide a valid token.",
                "UNAUTHORIZED", request);
    }

    /**
     * Handles requests where the user is authenticated but not authorised.
     * e.g. trying to access another user's conversation.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied for user on {}", request.getRequestURI());
        return build(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource.",
                "FORBIDDEN", request);
    }

    // =========================================================
    // Fallback
    // =========================================================

    /**
     * Catches anything not handled above.
     * Logs the full stack trace (it's unexpected) but returns
     * a generic message — never expose internal details to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                "INTERNAL_ERROR", request);
    }

    // =========================================================
    // Builder helper
    // =========================================================

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String message,
            String code,
            HttpServletRequest request) {

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .code(code)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(body);
    }
}