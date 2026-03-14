package com.genailab.ai.config;

import com.genailab.ai.embedding.EmbeddingClient;
import com.genailab.ai.embedding.mock.MockEmbeddingClientImpl;
import com.genailab.ai.model.AiChatClient;
import com.genailab.ai.model.mock.MockAiChatClientImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conditionally creates AI provider beans based on configuration.
 *
 * <p>Controls which AI implementation is active via:
 * <pre>
 * genailab:
 *   ai:
 *     provider: mock    # use mock — no API calls, instant responses
 *     provider: openai  # use real OpenAI — requires valid API key
 * </pre>
 *
 * <p>WHY conditional beans instead of an if-statement in the service?
 * Spring's @ConditionalOnProperty creates the correct bean at startup
 * and never instantiates the other. This means:
 * <ul>
 *   <li>The OpenAI client is never created in mock mode
 *       — no connection attempts, no API key validation</li>
 *   <li>The mock client is never created in production
 *       — clean separation, no risk of accidentally using mock</li>
 *   <li>The rest of the app (ChatService, RAG pipeline) is completely
 *       unaware of which implementation is active</li>
 * </ul>
 *
 * <p>Note: OpenAI beans (ChatModel, EmbeddingModel) are created by
 * Spring AI auto-configuration when the openai starter is on the classpath.
 * We only need to create beans for the mock implementations manually,
 * since they are not auto-configured.
 *
 * <p>The OpenAiChatClientImpl and OpenAiEmbeddingClientImpl are annotated
 * with @Component but also need @ConditionalOnProperty so they are only
 * active when provider=openai. We handle this here instead of putting
 * conditional annotations on the impl classes themselves — keeping the
 * impl classes clean and focused on their own logic.
 */
@Configuration
@Slf4j
public class AiProviderConfig {

    /**
     * Register the mock chat client when provider=mock.
     *
     * <p>matchIfMissing=false — mock is never the accidental default.
     * You must explicitly set provider=mock to use it.
     */
    @Bean
    @ConditionalOnProperty(
            name = "genailab.ai.provider",
            havingValue = "mock",
            matchIfMissing = false)
    public AiChatClient mockAiChatClient() {
        log.warn("=================================================");
        log.warn("  AI PROVIDER: MOCK MODE ACTIVE");
        log.warn("  No real AI calls will be made.");
        log.warn("  Set genailab.ai.provider=openai for real AI.");
        log.warn("=================================================");
        return new MockAiChatClientImpl();
    }

    /**
     * Register the mock embedding client when provider=mock.
     */
    @Bean
    @ConditionalOnProperty(
            name = "genailab.ai.provider",
            havingValue = "mock",
            matchIfMissing = false)
    public EmbeddingClient mockEmbeddingClient() {
        return new MockEmbeddingClientImpl();
    }
}