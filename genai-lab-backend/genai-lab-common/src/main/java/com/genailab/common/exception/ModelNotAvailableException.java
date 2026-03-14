package com.genailab.common.exception;

/**
 * Thrown when the model exists in ai_model_configs but is_active = false.
 * Maps to HTTP 503 Service Unavailable — model is known but currently disabled.
 */
public class ModelNotAvailableException extends AiException {

    public ModelNotAvailableException(String modelId) {
        super(
                "AI model '" + modelId + "' is currently unavailable. " +
                        "Please select a different model.",
                "MODEL_NOT_AVAILABLE"
        );
    }
}