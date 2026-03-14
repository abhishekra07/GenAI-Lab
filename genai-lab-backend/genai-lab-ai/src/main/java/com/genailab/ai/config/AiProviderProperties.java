package com.genailab.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Type-safe binding for all AI provider configuration.
 *
 * <p>Example yaml:
 * <pre>
 * genailab:
 *   ai:
 *     default-model: gpt-4o-mini
 *     providers:
 *       openai:
 *         enabled: true
 *         api-key: ${OPENAI_API_KEY:}
 *       mock:
 *         enabled: true
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "genailab.ai")
public class AiProviderProperties {

    /**
     * Default model used when a request does not specify one.
     * Must match a model_key in the ai_model_configs table.
     */
    private String defaultModel = "gpt-4o-mini";

    /**
     * Per-provider configuration map.
     * Key = provider name (e.g. "openai", "anthropic", "ollama", "mock")
     * Value = provider-specific settings
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    @Data
    public static class ProviderConfig {

        /**
         * Whether this provider is enabled.
         * Even if an API key is present, the provider is skipped if enabled=false.
         * Useful for temporarily disabling a provider without removing credentials.
         */
        private boolean enabled = false;

        /**
         * API key for cloud providers (OpenAI, Anthropic).
         * Empty string means not configured — provider will be skipped.
         */
        private String apiKey = "";

        /**
         * Base URL for the provider API.
         * Defaults work for standard endpoints.
         * Override for proxies, Azure OpenAI, or local Ollama.
         */
        private String baseUrl = "";

        /**
         * Default chat model for this provider.
         * Example: "gpt-4o-mini" for OpenAI, "llama3.2" for Ollama.
         */
        private String chatModel = "";

        /**
         * Default embedding model for this provider.
         * Example: "text-embedding-3-small" for OpenAI.
         */
        private String embeddingModel = "";

        /**
         * Whether this provider has a valid API key configured.
         * Used by AiProviderRegistry to decide whether to register this provider.
         */
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        /**
         * Whether this provider has a base URL configured.
         * Used for local providers like Ollama that don't need an API key.
         */
        public boolean hasBaseUrl() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }
}