package com.genailab.common.exception;

/**
 * Thrown when the provider returns a rate limit response (HTTP 429).
 * Maps to HTTP 429 Too Many Requests.
 * The frontend should display a "please wait and try again" message.
 */
public class AiRateLimitException extends AiException {

    public AiRateLimitException(String provider) {
        super(
                "AI provider '" + provider + "' rate limit exceeded. " +
                        "Please wait a moment and try again.",
                "RATE_LIMIT_EXCEEDED"
        );
    }
}