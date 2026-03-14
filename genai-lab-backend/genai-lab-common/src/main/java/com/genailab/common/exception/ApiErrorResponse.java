package com.genailab.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Standard error response shape returned for ALL API errors.
 *
 * <p>Every error the frontend receives has this structure:
 * <pre>
 * {
 *   "status": 503,
 *   "error": "Service Unavailable",
 *   "message": "AI provider 'anthropic' is not configured.",
 *   "code": "PROVIDER_NOT_AVAILABLE",
 *   "timestamp": "2026-03-14T13:00:00Z",
 *   "path": "/api/v1/conversations/abc/messages"
 * }
 * </pre>
 *
 * <p>The {@code code} field is machine-readable — frontend can use it
 * to show specific UI states without parsing the message string.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private int status;
    private String error;
    private String message;
    private String code;          // machine-readable error code
    private String path;          // request path that caused the error
    private Instant timestamp;
}