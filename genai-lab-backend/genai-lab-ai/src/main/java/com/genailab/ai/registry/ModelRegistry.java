package com.genailab.ai.registry;

import com.genailab.ai.model.AiChatClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves a provider name to the correct {@link AiChatClient} implementation.
 *
 * <p>When a chat request comes in with modelId="gpt-4o-mini", the system
 * needs to know that this is an OpenAI model and route it to
 * {@code OpenAiChatClientImpl}. That routing lives here.
 *
 * <p>Adding a new provider (e.g. Claude) requires only:
 * <ol>
 *   <li>Creating a new {@code ClaudeChatClientImpl} that implements AiChatClient</li>
 *   <li>Returning "anthropic" from its {@code getProvider()} method</li>
 * </ol>
 *
 * <p>The modelId-to-provider mapping is resolved via the ai_model_configs table.
 * The calling service first looks up the model config to get the provider string,
 * then calls {@link #getClientForProvider} with that provider string.
 */
@Component
@Slf4j
public class ModelRegistry {

    private final Map<String, AiChatClient> clientsByProvider;

    /**
     * Spring injects all AiChatClient beans as a list automatically.
     * We index them by provider for fast lookup.
     */
    public ModelRegistry(List<AiChatClient> clients) {
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(
                        AiChatClient::getProvider,
                        Function.identity()
                ));
    }

    @PostConstruct
    public void logRegisteredProviders() {
        log.info("AI Model Registry initialized with providers: {}",
                clientsByProvider.keySet());
    }

    /**
     * Get the chat client for a specific provider.
     *
     * @param provider the provider name, e.g. "openai", "anthropic"
     * @return the AiChatClient implementation for that provider
     * @throws IllegalArgumentException if no client is registered for the provider
     */
    public AiChatClient getClientForProvider(String provider) {
        AiChatClient client = clientsByProvider.get(provider);
        if (client == null) {
            throw new IllegalArgumentException(
                    "No AI client registered for provider: '" + provider +
                            "'. Registered providers: " + clientsByProvider.keySet());
        }
        return client;
    }

    /**
     * Check if a provider is available.
     */
    public boolean hasProvider(String provider) {
        return clientsByProvider.containsKey(provider);
    }
}