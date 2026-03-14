package com.genailab.common.exception;

/**
 * Thrown when the requested modelId does not exist in ai_model_configs.
 * Maps to HTTP 400 Bad Request — the client sent an invalid model ID.
 */
public class ModelNotFoundException extends AiException {

    public ModelNotFoundException(String modelId) {
        super(
                "AI model not found: '" + modelId + "'. " +
                        "Use GET /api/v1/models to see available models.",
                "MODEL_NOT_FOUND"
        );
    }
}