package com.genailab.chat.service;

import com.genailab.ai.model.*;
import com.genailab.ai.registry.ModelRegistry;
import com.genailab.chat.domain.Conversation;
import com.genailab.chat.domain.Message;
import com.genailab.chat.dto.MessageResponse;
import com.genailab.chat.dto.SendMessageRequest;
import com.genailab.chat.repository.ConversationRepository;
import com.genailab.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core chat service — orchestrates the full send-message flow.
 *
 * <p>Flow for a streaming message:
 * <ol>
 *   <li>Validate the conversation belongs to the user</li>
 *   <li>Save the user message to DB</li>
 *   <li>Load conversation history for AI context</li>
 *   <li>Call AI provider via AiChatClient abstraction</li>
 *   <li>Stream response chunks to the SSE emitter</li>
 *   <li>Save the complete assistant response to DB</li>
 *   <li>Update conversation's last_message_at</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    // Max messages to include in AI context to avoid hitting token limits.
    // Includes the system prompt + last N messages. Tune based on model context window.
    private static final int MAX_CONTEXT_MESSAGES = 20;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ModelRegistry modelRegistry;

    // Virtual thread executor for SSE streaming.
    // WHY virtual threads? SSE requires a thread to stay alive for the duration
    // of the stream. With traditional threads this would be expensive.
    // Java 21 virtual threads are lightweight — thousands can run concurrently.
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Load all messages for a conversation (for displaying chat history).
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(UUID conversationId, UUID userId) {
        // Verify ownership before returning messages
        conversationService.findOwnedConversation(conversationId, userId);

        return messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Send a message and stream the AI response via SSE.
     *
     * <p>Returns an SseEmitter immediately. The actual streaming happens
     * asynchronously on a virtual thread — the HTTP response stays open
     * and chunks are pushed as they arrive from OpenAI.
     *
     * <p>SseEmitter has a timeout — we set it to 3 minutes which is more
     * than enough for any AI response. The emitter is completed or errored
     * inside the async task.
     */
    public SseEmitter streamMessage(UUID conversationId, SendMessageRequest request,
                                    UUID userId) {

        Conversation conversation = conversationService
                .findOwnedConversation(conversationId, userId);

        // Resolve which model to use — request can override conversation default
        String modelId = request.getModelId() != null
                ? request.getModelId()
                : conversation.getModelId();

        // Save user message immediately — it's in the DB before AI call starts
        Message userMessage = saveMessage(
                conversationId, "user", request.getContent(), null, null, modelId, false);

        // Create SSE emitter with 3-minute timeout
        SseEmitter emitter = new SseEmitter(180_000L);

        // Run the streaming on a virtual thread so we don't block the HTTP thread
        streamExecutor.execute(() ->
                executeStream(emitter, conversation, userMessage, modelId, request.getContent()));

        return emitter;
    }

    /**
     * Send a message and return the full response (non-streaming).
     * Used internally by the RAG pipeline and for simple integrations.
     */
    @Transactional
    public MessageResponse sendMessage(UUID conversationId, SendMessageRequest request,
                                       UUID userId) {

        Conversation conversation = conversationService
                .findOwnedConversation(conversationId, userId);

        String modelId = request.getModelId() != null
                ? request.getModelId()
                : conversation.getModelId();

        saveMessage(conversationId, "user", request.getContent(),
                null, null, modelId, false);

        List<AiMessage> contextMessages = buildContextMessages(conversation, request.getContent());

        AiChatRequest aiRequest = AiChatRequest.builder()
                .modelId(modelId)
                .messages(contextMessages)
                .build();

        // Resolve provider from model registry — currently always "openai"
        // When multiple providers exist, model configs table will drive this
        AiChatClient client = modelRegistry.getClientForProvider("openai");
        AiChatResponse aiResponse = client.chat(aiRequest);

        Message assistantMessage = saveMessage(
                conversationId,
                "assistant",
                aiResponse.getContent(),
                aiResponse.getTokenUsage() != null ? aiResponse.getTokenUsage().getPromptTokens() : null,
                aiResponse.getTokenUsage() != null ? aiResponse.getTokenUsage().getCompletionTokens() : null,
                modelId,
                false);

        conversationRepository.updateLastMessageAt(conversationId, Instant.now());

        return toResponse(assistantMessage);
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Execute the streaming flow on a virtual thread.
     *
     * <p>This method runs entirely off the HTTP thread. It:
     * <ol>
     *   <li>Builds context messages from conversation history</li>
     *   <li>Calls the AI provider's stream endpoint</li>
     *   <li>Sends each chunk to the SSE emitter</li>
     *   <li>Accumulates the full response</li>
     *   <li>Saves the complete assistant message to DB</li>
     *   <li>Completes or errors the emitter</li>
     * </ol>
     */
    private void executeStream(SseEmitter emitter, Conversation conversation,
                               Message userMessage, String modelId, String userContent) {

        StringBuilder fullResponse = new StringBuilder();

        try {
            List<AiMessage> contextMessages = buildContextMessages(conversation, userContent);

            AiChatRequest aiRequest = AiChatRequest.builder()
                    .modelId(modelId)
                    .messages(contextMessages)
                    .stream(true)
                    .build();

            AiChatClient client = modelRegistry.getClientForProvider("openai");
            Flux<AiStreamChunk> stream = client.streamChat(aiRequest);

            // Subscribe to the Flux and block on this virtual thread.
            // blockLast() subscribes and waits for the stream to complete.
            // Each onNext sends the chunk to the SSE emitter.
            stream.doOnNext(chunk -> {
                try {
                    if (!chunk.isDone() && chunk.getContent() != null
                            && !chunk.getContent().isEmpty()) {
                        fullResponse.append(chunk.getContent());
                        // Send SSE event with the chunk text
                        emitter.send(SseEmitter.event()
                                .name("chunk")
                                .data(chunk.getContent()));
                    }
                    if (chunk.isDone()) {
                        // Send a "done" event so the frontend knows the stream ended
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data("[DONE]"));
                    }
                } catch (IOException e) {
                    log.warn("Failed to send SSE chunk — client likely disconnected: {}",
                            e.getMessage());
                }
            }).blockLast();

            // Save the complete assembled response to DB
            saveMessage(
                    conversation.getId(),
                    "assistant",
                    fullResponse.toString(),
                    null,   // prompt tokens not available in streaming mode
                    null,   // completion tokens not available in streaming mode
                    modelId,
                    false);

            conversationRepository.updateLastMessageAt(conversation.getId(), Instant.now());

            // Auto-generate a title from the first user message
            autoGenerateTitle(conversation, userContent);

            emitter.complete();

        } catch (Exception e) {
            log.error("Streaming failed for conversation {}: {}",
                    conversation.getId(), e.getMessage());

            // Save error message to DB so the user can see what went wrong
            saveMessage(conversation.getId(), "assistant",
                    "Sorry, an error occurred: " + e.getMessage(),
                    null, null, modelId, true);

            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Generation failed: " + e.getMessage()));
            } catch (IOException ignored) {}

            emitter.completeWithError(e);
        }
    }

    /**
     * Build the message list to send to the AI.
     *
     * <p>Structure:
     * <ol>
     *   <li>System prompt (if the conversation has one)</li>
     *   <li>Last N messages from conversation history</li>
     *   <li>The new user message</li>
     * </ol>
     *
     * <p>We cap history at MAX_CONTEXT_MESSAGES to avoid token limit issues
     * on long conversations. The most recent messages are kept.
     */
    private List<AiMessage> buildContextMessages(Conversation conversation, String newUserContent) {
        List<AiMessage> messages = new java.util.ArrayList<>();

        // Add system prompt first if present
        if (conversation.getSystemPrompt() != null
                && !conversation.getSystemPrompt().isBlank()) {
            messages.add(AiMessage.system(conversation.getSystemPrompt()));
        }

        // Load recent history — in reverse order from DB, then reverse back
        List<Message> history = messageRepository.findRecentMessages(
                conversation.getId(), MAX_CONTEXT_MESSAGES);

        // findRecentMessages returns DESC order (newest first) — reverse to chronological
        java.util.Collections.reverse(history);

        history.forEach(msg ->
                messages.add(new AiMessage(
                        AiRole.valueOf(msg.getRole().toUpperCase()),
                        msg.getContent())));

        // Add the new user message at the end
        messages.add(AiMessage.user(newUserContent));

        return messages;
    }

    /**
     * Persist a message to the database.
     */
    @Transactional
    protected Message saveMessage(UUID conversationId, String role, String content,
                                  Integer promptTokens, Integer completionTokens, String modelUsed,
                                  boolean isError) {

        Message message = Message.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .tokenCountPrompt(promptTokens)
                .tokenCountCompletion(completionTokens)
                .modelUsed(modelUsed)
                .isError(isError)
                .build();

        return messageRepository.save(message);
    }

    /**
     * Auto-generate a conversation title from the first user message.
     * Only runs when the conversation still has the default "New Conversation" title.
     * Truncates to 60 characters for a clean sidebar display.
     */
    private void autoGenerateTitle(Conversation conversation, String firstMessage) {
        if ("New Conversation".equals(conversation.getTitle())) {
            String title = firstMessage.length() > 60
                    ? firstMessage.substring(0, 57) + "..."
                    : firstMessage;
            conversation.setTitle(title);
            conversationRepository.save(conversation);
        }
    }

    private MessageResponse toResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .role(m.getRole())
                .content(m.getContent())
                .tokenCountPrompt(m.getTokenCountPrompt())
                .tokenCountCompletion(m.getTokenCountCompletion())
                .modelUsed(m.getModelUsed())
                .isError(m.isError())
                .createdAt(m.getCreatedAt())
                .build();
    }
}