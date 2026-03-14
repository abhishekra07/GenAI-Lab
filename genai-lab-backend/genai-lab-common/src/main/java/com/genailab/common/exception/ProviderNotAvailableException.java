package com.genailab.common.exception;

/**
 * Thrown when the model's provider is not registered in AiProviderRegistry.
 * This happens when the provider's API key is missing or empty in config.
 * Maps to HTTP 503 Service Unavailable.
 */
public class ProviderNotAvailableException extends AiException {

    public ProviderNotAvailableException(String provider) {
        super(
                "AI provider '" + provider + "' is not configured. " +
                        "The required API key may be missing from the server configuration.",
                "PROVIDER_NOT_AVAILABLE"
        );
    }
}