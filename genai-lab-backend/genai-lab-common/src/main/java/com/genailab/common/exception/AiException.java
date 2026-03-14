package com.genailab.common.exception;

/**
 * Base exception for all AI-related failures.
 * All specific AI exceptions extend this — allows catching
 * all AI errors in one place when needed.
 */
public class AiException extends RuntimeException {

    private final String errorCode;

    public AiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}