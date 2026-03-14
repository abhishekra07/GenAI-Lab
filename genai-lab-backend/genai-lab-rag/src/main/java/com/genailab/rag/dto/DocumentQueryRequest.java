package com.genailab.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class DocumentQueryRequest {

    @NotBlank(message = "Question is required")
    @Size(max = 2000, message = "Question cannot exceed 2000 characters")
    private String question;

    /**
     * Optional model override.
     * If null, uses the default model from config.
     * Must match a model_key in ai_model_configs.
     */
    private String modelId;
}