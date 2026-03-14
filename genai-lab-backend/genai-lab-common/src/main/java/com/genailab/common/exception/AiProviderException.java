package com.genailab.common.exception;

/**
 * Thrown when a configured provider returns an error during an AI call.
 * Examples: invalid API key (401), provider down (500), context too long (400).
 * Maps to HTTP 502 Bad Gateway — we reached the provider but it failed.
 */
public class AiProviderException extends AiException {

    public AiProviderException(String provider, String detail) {
        super(
                "AI provider '" + provider + "' returned an error: " + detail,
                "PROVIDER_ERROR"
        );
    }

    public AiProviderException(String provider, String detail, Throwable cause) {
        super(
                "AI provider '" + provider + "' returned an error: " + detail,
                "PROVIDER_ERROR",
                cause
        );
    }
}