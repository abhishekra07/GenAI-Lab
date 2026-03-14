package com.genailab.ai.model;

import reactor.core.publisher.Flux;

/**
 * Core abstraction for AI chat completion.
 *
 * <p>This is the most important interface in the AI module.
 * Every feature that needs AI (chat, RAG query answering) depends
 * on this interface — never on a specific provider implementation.
 *
 * <p>Two methods:
 * <ul>
 *   <li>{@link #chat} — blocking, returns the full response at once.
 *       Used for RAG pipeline where we need the full answer before
 *       assembling the response.</li>
 *   <li>{@link #streamChat} — non-blocking, returns a reactive stream
 *       of chunks. Used for the chat UI's real-time streaming effect.</li>
 * </ul>
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code OpenAiChatClientImpl} — current implementation</li>
 * </ul>
 */
public interface AiChatClient {

    /**
     * Send a chat request and wait for the full response.
     *
     * @param request the chat request with messages, model, and parameters
     * @return the complete AI response including token usage
     */
    AiChatResponse chat(AiChatRequest request);

    /**
     * Send a chat request and receive the response as a stream of chunks.
     *
     * <p>The returned Flux emits chunks as they arrive from the provider.
     * The final chunk will have {@code done = true}.
     *
     * <p>Callers must subscribe to this Flux to start the stream.
     * Nothing is sent to the AI provider until subscription occurs.
     *
     * @param request the chat request — stream flag should be true
     * @return a Flux of response chunks, completing when generation is done
     */
    Flux<AiStreamChunk> streamChat(AiChatRequest request);

    /**
     * The provider identifier this implementation handles.
     * Matches the {@code provider} column in ai_model_configs.
     * Example: "openai", "anthropic", "ollama"
     */
    String getProvider();
}