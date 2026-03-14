package com.genailab.ai.model.mock;

import com.genailab.ai.model.*;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * Mock AI chat client for development and testing.
 *
 * <p>Behaves exactly like a real AI client from the application's perspective:
 * <ul>
 *   <li>Implements the same {@link AiChatClient} interface</li>
 *   <li>Returns realistic streaming chunks via Flux</li>
 *   <li>Populates token usage counts</li>
 *   <li>Responds contextually based on the last user message</li>
 *   <li>Simulates realistic streaming delay between chunks</li>
 * </ul>
 *
 * <p>This class is NOT annotated with @Component — it is only instantiated
 * by {@link com.genailab.ai.config.AiProviderConfig} when the mock
 * provider is configured. This ensures it never accidentally runs in
 * production (where no mock config would exist).
 *
 * <p>Activation:
 * <pre>
 * genailab:
 *   ai:
 *     provider: mock
 * </pre>
 */
@Slf4j
public class MockAiChatClientImpl implements AiChatClient {

    private static final String PROVIDER = "mock";
    private static final Random RANDOM = new Random();

    private final MeterRegistry meterRegistry;

    public MockAiChatClientImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // Realistic mock responses keyed by topic keywords in the user message
    private static final List<String> GENERIC_RESPONSES = List.of(
            "That's a great question. Based on the information provided, I can explain this concept in detail. " +
                    "The key thing to understand here is that there are multiple factors at play. " +
                    "First, we need to consider the context. Second, we should look at the underlying principles. " +
                    "Finally, we can draw some practical conclusions from this analysis.",

            "I understand what you're asking. Let me break this down step by step. " +
                    "The first step involves understanding the core concept. " +
                    "Once we have that foundation, we can explore the more nuanced aspects. " +
                    "This approach leads to a clearer overall understanding of the topic.",

            "Excellent question! This is actually a fascinating area to explore. " +
                    "There are several important points worth noting here. " +
                    "The most significant aspect is how these elements interact with each other. " +
                    "When we consider all the available information, a clear pattern emerges.",

            "Let me provide a thorough response to your question. " +
                    "This topic has several important dimensions worth exploring. " +
                    "From a technical standpoint, the implementation details matter greatly. " +
                    "From a practical standpoint, the real-world applications are equally important."
    );

    private static final String RAG_RESPONSE =
            "Based on the document you provided, I can answer your question directly. " +
                    "The document contains relevant information on this topic. " +
                    "According to the content, the key points are as follows. " +
                    "First, the document establishes the foundational concepts clearly. " +
                    "Second, it provides specific details that directly address your question. " +
                    "In summary, the document supports the following conclusion regarding your query.";

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        log.info("[MOCK AI] Processing non-streaming chat request. Model: {}, Messages: {}",
                request.getModelId(), request.getMessages().size());

        String response = buildResponse(request);
        int promptTokens = estimateTokens(request.getMessages());
        int completionTokens = estimateTokens(response);

        log.info("[MOCK AI] Response generated. Prompt tokens: {}, Completion tokens: {}",
                promptTokens, completionTokens);

        String modelId = request.getModelId() != null ? request.getModelId() : "mock-model";

        // Record metrics — same as real providers so dashboards work uniformly
        meterRegistry.counter("genailab.ai.requests.total",
                "provider", PROVIDER, "model", modelId, "status", "success").increment();
        meterRegistry.counter("genailab.ai.tokens.used",
                        "provider", PROVIDER, "model", modelId, "type", "prompt")
                .increment(promptTokens);
        meterRegistry.counter("genailab.ai.tokens.used",
                        "provider", PROVIDER, "model", modelId, "type", "completion")
                .increment(completionTokens);

        return AiChatResponse.builder()
                .content(response)
                .modelUsed(modelId)
                .tokenUsage(AiChatResponse.TokenUsage.builder()
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(promptTokens + completionTokens)
                        .build())
                .build();
    }

    @Override
    public Flux<AiStreamChunk> streamChat(AiChatRequest request) {
        log.info("[MOCK AI] Processing streaming chat request. Model: {}, Messages: {}",
                request.getModelId(), request.getMessages().size());

        String fullResponse = buildResponse(request);
        String[] words = fullResponse.split(" ");

        int promptTokens = estimateTokens(request.getMessages());
        int completionTokens = estimateTokens(fullResponse);

        // Emit words one at a time with a small delay to simulate real streaming.
        // Uses Flux.interval to create timed emissions — each word arrives
        // with a realistic delay between 30-80ms, just like a real LLM would stream.
        return Flux.range(0, words.length)
                .delayElements(Duration.ofMillis(40))
                .map(i -> {
                    String word = words[i] + (i < words.length - 1 ? " " : "");
                    return AiStreamChunk.of(word);
                })
                .concatWith(Flux.defer(() -> {
                    log.info("[MOCK AI] Stream complete. Total tokens: {}",
                            promptTokens + completionTokens);
                    return Flux.just(AiStreamChunk.done(
                            AiChatResponse.TokenUsage.builder()
                                    .promptTokens(promptTokens)
                                    .completionTokens(completionTokens)
                                    .totalTokens(promptTokens + completionTokens)
                                    .build()
                    ));
                }));
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Build a contextual response based on the conversation content.
     *
     * <p>Checks for keywords in the last user message to return
     * a more relevant mock response. This makes testing feel realistic —
     * asking about a document returns a document-style answer,
     * asking a code question returns a code-style answer, etc.
     */
    private String buildResponse(AiChatRequest request) {
        String lastUserMessage = extractLastUserMessage(request.getMessages());

        if (lastUserMessage == null) {
            return GENERIC_RESPONSES.get(0);
        }

        String lower = lastUserMessage.toLowerCase();

        // RAG/document queries
        if (lower.contains("document") || lower.contains("according to")
                || lower.contains("based on") || lower.contains("summarize")
                || lower.contains("what does") || lower.contains("pdf")) {
            return RAG_RESPONSE;
        }

        // Code questions
        if (lower.contains("code") || lower.contains("java") || lower.contains("spring")
                || lower.contains("implement") || lower.contains("function")
                || lower.contains("class") || lower.contains("method")) {
            return "Here is how you would approach this in Java with Spring Boot. " +
                    "The implementation involves creating a service class with the appropriate annotations. " +
                    "You would use @Service for the business logic layer and @Repository for data access. " +
                    "The key pattern here is dependency injection through constructor injection. " +
                    "This ensures testability and follows the single responsibility principle.";
        }

        // Greeting
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("hey")
                || lower.contains("how are you")) {
            return "Hello! I'm the GenAI Lab assistant running in mock mode. " +
                    "I'm here to help you test the application. " +
                    "Feel free to ask me anything — I'll do my best to provide helpful responses. " +
                    "Note: I'm currently in mock mode, so my responses are simulated.";
        }

        // Math / simple facts
        if (lower.contains("what is") && (lower.contains("+") || lower.contains("plus")
                || lower.contains("minus") || lower.contains("times"))) {
            return "The answer to your calculation is 42. " +
                    "(Note: This is a mock response — in production the AI would compute the actual answer.)";
        }

        // Default — pick a random generic response for variety
        return GENERIC_RESPONSES.get(RANDOM.nextInt(GENERIC_RESPONSES.size()));
    }

    private String extractLastUserMessage(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == AiRole.USER) {
                return messages.get(i).getContent();
            }
        }
        return null;
    }

    /**
     * Estimate token count from a string.
     * Approximation: 1 token ≈ 4 characters (standard rule of thumb for English text).
     */
    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return Math.max(1, text.length() / 4);
    }

    /**
     * Estimate total token count across all messages in a request.
     */
    private int estimateTokens(List<AiMessage> messages) {
        if (messages == null) return 0;
        return messages.stream()
                .mapToInt(m -> estimateTokens(m.getContent()))
                .sum();
    }
}