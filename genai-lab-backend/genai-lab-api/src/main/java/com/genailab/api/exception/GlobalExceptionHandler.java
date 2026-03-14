package com.genailab.api.exception;

import com.genailab.common.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler — intercepts all exceptions thrown from
 * any controller and maps them to consistent {@link ApiErrorResponse} shapes.
 *
 * <p>WHY centralise exception handling here?
 * Without this, Spring returns its own default error format which:
 * <ul>
 *   <li>Leaks stack traces in some configurations</li>
 *   <li>Has inconsistent structure across different error types</li>
 *   <li>Does not include our machine-readable error codes</li>
 * </ul>
 *
 * <p>With @RestControllerAdvice every exception from every controller
 * flows through here before reaching the client. One place to:
 * <ul>
 *   <li>Control exactly what the frontend sees</li>
 *   <li>Log at the right level (warn vs error)</li>
 *   <li>Never leak internal details (stack traces, DB errors)</li>
 * </ul>
 *
 * <p>Handler priority — Spring picks the most specific handler:
 * specific exceptions are caught before the generic fallback at the bottom.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // =========================================================
    // AI exceptions
    // =========================================================

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
     * Overrides ResponseEntityExceptionHandler to use our ApiErrorResponse format.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", message);

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed: " + message)
                .code("VALIDATION_ERROR")
                .timestamp(java.time.Instant.now())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles unsupported content type.
     * Overrides ResponseEntityExceptionHandler to log detailed info
     * and return our standard ApiErrorResponse format.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String requestUri = ((jakarta.servlet.http.HttpServletRequest)
                ((org.springframework.web.context.request.ServletWebRequest) request)
                        .getNativeRequest()).getRequestURI();
        String contentType = ((jakarta.servlet.http.HttpServletRequest)
                ((org.springframework.web.context.request.ServletWebRequest) request)
                        .getNativeRequest()).getContentType();

        log.error("Unsupported Content-Type on {}: received=[{}], supported={}",
                requestUri, contentType, ex.getSupportedMediaTypes());

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .error(HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase())
                .message("Content-Type '" + contentType + "' is not supported. " +
                        "For file uploads use multipart/form-data.")
                .code("UNSUPPORTED_MEDIA_TYPE")
                .path(requestUri)
                .timestamp(java.time.Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
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