package com.genailab.ai.service;

import com.genailab.ai.domain.AiModelConfig;
import com.genailab.ai.dto.ModelResponse;
import com.genailab.ai.registry.AiProviderRegistry;
import com.genailab.ai.repository.AiModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Provides the list of available AI models for the frontend model selector.
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelService {

    private final AiModelConfigRepository modelConfigRepository;
    private final AiProviderRegistry aiProviderRegistry;

    @Transactional(readOnly = true)
    public List<ModelResponse> getAvailableModels() {
        return modelConfigRepository
                .findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ModelResponse toResponse(AiModelConfig config) {
        boolean available = aiProviderRegistry.isProviderAvailable(config.getProvider());

        return ModelResponse.builder()
                .id(config.getId())
                .modelKey(config.getModelKey())
                .displayName(config.getDisplayName())
                .provider(config.getProvider())
                .contextWindow(config.getContextWindow())
                .isDefault(config.isDefault())
                .available(available)
                .capabilities(config.getCapabilities())
                .build();
    }
}