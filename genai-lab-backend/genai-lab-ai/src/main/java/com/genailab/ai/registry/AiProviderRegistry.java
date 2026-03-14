package com.genailab.ai.registry;

import com.genailab.ai.config.AiProviderProperties;
import com.genailab.ai.domain.AiModelConfig;
import com.genailab.ai.embedding.EmbeddingClient;
import com.genailab.ai.embedding.mock.MockEmbeddingClientImpl;
import com.genailab.ai.embedding.openai.OpenAiEmbeddingClientImpl;
import com.genailab.ai.model.AiChatClient;
import com.genailab.ai.model.mock.MockAiChatClientImpl;
import com.genailab.ai.model.openai.OpenAiChatClientImpl;
import com.genailab.ai.repository.AiModelConfigRepository;
import com.genailab.common.exception.ModelNotFoundException;
import com.genailab.common.exception.ModelNotAvailableException;
import com.genailab.common.exception.ProviderNotAvailableException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for AI providers.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>At startup: reads {@link AiProviderProperties} and initialises
 *       only the providers that are properly configured</li>
 *   <li>Per request: resolves a modelId → provider → AiChatClient
 *       with full validation and meaningful exceptions</li>
 *   <li>Graceful degradation: missing/empty API key = provider skipped,
 *       not a startup failure</li>
 * </ul>
 *
 * <p>Adding a new provider:
 * <ol>
 *   <li>Create a new AiChatClient implementation</li>
 *   <li>Add a new case in {@link #initializeChatClients()}</li>
 *   <li>Add provider config in application.yml</li>
 *   <li>Insert model rows in ai_model_configs table</li>
 * </ol>
 * No changes needed in ChatService or anywhere else.
 */
@Component
@Slf4j
public class AiProviderRegistry {

    private final AiProviderProperties properties;
    private final AiModelConfigRepository modelConfigRepository;
    private final MeterRegistry meterRegistry;

    // Spring AI auto-configured beans — may be null if starter not on classpath
    // We use @Autowired(required=false) so startup doesn't fail if unavailable
    @Autowired(required = false)
    private ChatModel springAiChatModel;

    @Autowired(required = false)
    private EmbeddingModel springAiEmbeddingModel;

    // Registered providers — populated at startup
    private final Map<String, AiChatClient> chatClients = new HashMap<>();
    private final Map<String, EmbeddingClient> embeddingClients = new HashMap<>();

    public AiProviderRegistry(
            AiProviderProperties properties,
            AiModelConfigRepository modelConfigRepository,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.modelConfigRepository = modelConfigRepository;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing AI Provider Registry...");
        initializeChatClients();
        initializeEmbeddingClients();
        logRegisteredProviders();
    }

    // =========================================================
    // Public API — used by ChatService and RAG pipeline
    // =========================================================

    /**
     * Resolve the correct AiChatClient for a given modelId.
     *
     * <p>Full validation chain:
     * <ol>
     *   <li>Model exists in ai_model_configs → else ModelNotFoundException (400)</li>
     *   <li>Model is active → else ModelNotAvailableException (503)</li>
     *   <li>Provider is registered → else ProviderNotAvailableException (503)</li>
     * </ol>
     *
     * @param modelId the model key from the request (e.g. "gpt-4o-mini")
     * @return the AiChatClient that handles this model
     */
    public AiChatClient getChatClientForModel(String modelId) {
        AiModelConfig modelConfig = resolveAndValidateModel(modelId);
        String provider = modelConfig.getProvider();

        AiChatClient client = chatClients.get(provider);
        if (client == null) {
            throw new ProviderNotAvailableException(provider);
        }
        return client;
    }

    /**
     * Resolve the correct EmbeddingClient for a given modelId.
     * Same validation chain as getChatClientForModel.
     */
    public EmbeddingClient getEmbeddingClientForModel(String modelId) {
        AiModelConfig modelConfig = resolveAndValidateModel(modelId);
        String provider = modelConfig.getProvider();

        EmbeddingClient client = embeddingClients.get(provider);
        if (client == null) {
            throw new ProviderNotAvailableException(provider);
        }
        return client;
    }

    /**
     * Get the default EmbeddingClient — used by the RAG pipeline
     * when no specific model is requested for embedding.
     */
    public EmbeddingClient getDefaultEmbeddingClient() {
        String defaultModel = properties.getDefaultModel();
        return getEmbeddingClientForModel(defaultModel);
    }

    /**
     * Check if a provider is currently registered and available.
     */
    public boolean isProviderAvailable(String provider) {
        return chatClients.containsKey(provider);
    }

    /**
     * Get the model config for a modelId — used by ChatService
     * to store the model metadata with each message.
     */
    public AiModelConfig getModelConfig(String modelId) {
        return resolveAndValidateModel(modelId);
    }

    // =========================================================
    // Initialisation — runs once at startup
    // =========================================================

    private void initializeChatClients() {
        Map<String, AiProviderProperties.ProviderConfig> providers = properties.getProviders();

        // --- Mock provider ---
        // Always safe to initialise — no external dependencies
        AiProviderProperties.ProviderConfig mockConfig = providers.get("mock");
        if (mockConfig != null && mockConfig.isEnabled()) {
            chatClients.put("mock", new MockAiChatClientImpl(meterRegistry));
            log.info("  ✓ Mock AI provider registered");
        }

        // --- OpenAI provider ---
        AiProviderProperties.ProviderConfig openaiConfig = providers.get("openai");
        if (openaiConfig != null && openaiConfig.isEnabled()) {
            if (!openaiConfig.hasApiKey()) {
                log.warn("  ✗ OpenAI skipped — OPENAI_API_KEY is not set");
            } else if (springAiChatModel == null) {
                log.warn("  ✗ OpenAI skipped — Spring AI ChatModel bean not available");
            } else {
                chatClients.put("openai",
                        new OpenAiChatClientImpl(springAiChatModel, meterRegistry));
                log.info("  ✓ OpenAI provider registered");
            }
        }

        // --- Anthropic provider (future) ---
        AiProviderProperties.ProviderConfig anthropicConfig = providers.get("anthropic");
        if (anthropicConfig != null && anthropicConfig.isEnabled()) {
            if (!anthropicConfig.hasApiKey()) {
                log.warn("  ✗ Anthropic skipped — ANTHROPIC_API_KEY is not set");
            } else {
                // Placeholder — AnthropicChatClientImpl will be added later
                log.warn("  ✗ Anthropic skipped — implementation not yet available");
            }
        }

        // --- Ollama provider (future) ---
        AiProviderProperties.ProviderConfig ollamaConfig = providers.get("ollama");
        if (ollamaConfig != null && ollamaConfig.isEnabled()) {
            if (!ollamaConfig.hasBaseUrl()) {
                log.warn("  ✗ Ollama skipped — base URL is not set");
            } else {
                // Placeholder — OllamaChatClientImpl will be added later
                log.warn("  ✗ Ollama skipped — implementation not yet available");
            }
        }
    }

    private void initializeEmbeddingClients() {
        Map<String, AiProviderProperties.ProviderConfig> providers = properties.getProviders();

        // --- Mock embedding ---
        AiProviderProperties.ProviderConfig mockConfig = providers.get("mock");
        if (mockConfig != null && mockConfig.isEnabled()) {
            embeddingClients.put("mock", new MockEmbeddingClientImpl());
        }

        // --- OpenAI embedding ---
        AiProviderProperties.ProviderConfig openaiConfig = providers.get("openai");
        if (openaiConfig != null && openaiConfig.isEnabled()
                && openaiConfig.hasApiKey()
                && springAiEmbeddingModel != null) {
            embeddingClients.put("openai",
                    new OpenAiEmbeddingClientImpl(
                            springAiEmbeddingModel,
                            openaiConfig.getEmbeddingModel().isBlank()
                                    ? "text-embedding-3-small"
                                    : openaiConfig.getEmbeddingModel()
                    ));
        }
    }

    // =========================================================
    // Validation — runs per request
    // =========================================================

    /**
     * Validate a modelId against the database.
     * Throws meaningful exceptions for each failure case.
     */
    private AiModelConfig resolveAndValidateModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            // Fall back to default model if none specified
            modelId = properties.getDefaultModel();
        }

        Optional<AiModelConfig> config = modelConfigRepository.findByModelKey(modelId);

        if (config.isEmpty()) {
            throw new ModelNotFoundException(modelId);
        }

        AiModelConfig modelConfig = config.get();

        if (!modelConfig.isActive()) {
            throw new ModelNotAvailableException(modelId);
        }

        return modelConfig;
    }

    private void logRegisteredProviders() {
        if (chatClients.isEmpty()) {
            log.warn("=================================================");
            log.warn("  WARNING: No AI providers registered!");
            log.warn("  Check your API keys in application-dev-local.yml");
            log.warn("  or set genailab.ai.providers.mock.enabled=true");
            log.warn("=================================================");
        } else {
            log.info("AI Provider Registry ready. Active providers: {}", chatClients.keySet());
        }
    }
}