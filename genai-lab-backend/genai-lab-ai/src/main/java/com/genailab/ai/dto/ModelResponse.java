package com.genailab.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for a single AI model entry.
 * Used by GET /api/v1/models to populate the frontend model selector.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelResponse {

    private UUID id;
    private String modelKey;
    private String displayName;
    private String provider;
    private int contextWindow;
    private boolean isDefault;
    private boolean available;
    private Map<String, Object> capabilities;
}