package com.genailab.chat.service;

import com.genailab.ai.model.*;
import com.genailab.ai.registry.AiProviderRegistry;
import com.genailab.chat.domain.Conversation;
import com.genailab.chat.domain.Message;
import com.genailab.chat.dto.MessageResponse;
import com.genailab.chat.dto.SendMessageRequest;
import com.genailab.chat.repository.ConversationRepository;
import com.genailab.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    private static final int MAX_CONTEXT_MESSAGES = 20;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final AiProviderRegistry aiProviderRegistry;

    private final ExecutorService streamExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(UUID conversationId, UUID userId) {
        conversationService.findOwnedConversation(conversationId, userId);
        return messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SseEmitter streamMessage(
            UUID conversationId,
            SendMessageRequest request,
            UUID userId) {

        Conversation conversation = conversationService
                .findOwnedConversation(conversationId, userId);

        String modelId = request.getModelId() != null
                ? request.getModelId()
                : conversation.getModelId();

        // Save user message BEFORE handing off to virtual thread
        // so it's committed and visible immediately
        saveMessage(conversationId, "user", request.getContent(),
                null, null, modelId, false);

        SseEmitter emitter = new SseEmitter(180_000L);

        streamExecutor.execute(() ->
                executeStream(emitter, conversation, modelId, request.getContent()));

        return emitter;
    }

    @Transactional
    public MessageResponse sendMessage(
            UUID conversationId,
            SendMessageRequest request,
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

        AiChatClient client = aiProviderRegistry.getChatClientForModel(modelId);
        AiChatResponse aiResponse = client.chat(aiRequest);

        Message assistantMessage = saveMessage(
                conversationId, "assistant",
                aiResponse.getContent(),
                aiResponse.getTokenUsage() != null ? aiResponse.getTokenUsage().getPromptTokens() : null,
                aiResponse.getTokenUsage() != null ? aiResponse.getTokenUsage().getCompletionTokens() : null,
                modelId, false);

        updateLastMessageAt(conversationId);

        return toResponse(assistantMessage);
    }

    // =========================================================
    // Private — streaming
    // =========================================================

    /**
     * Executes the full streaming flow on a virtual thread.
     *
     * <p>IMPORTANT — never call emitter.completeWithError() here.
     * Doing so triggers Tomcat's async error dispatcher which re-processes
     * the request through Spring Security without a SecurityContext,
     * resulting in "Access Denied" noise in logs and a broken SSE stream.
     * Instead: send an error SSE event, then call emitter.complete().
     *
     * <p>All DB writes use REQUIRES_NEW propagation so they create
     * their own transactions — this thread has no ambient transaction.
     */
    private void executeStream(
            SseEmitter emitter,
            Conversation conversation,
            String modelId,
            String userContent) {

        StringBuilder fullResponse = new StringBuilder();

        try {
            List<AiMessage> contextMessages = buildContextMessages(conversation, userContent);

            AiChatRequest aiRequest = AiChatRequest.builder()
                    .modelId(modelId)
                    .messages(contextMessages)
                    .stream(true)
                    .build();

            AiChatClient client = aiProviderRegistry.getChatClientForModel(modelId);
            Flux<AiStreamChunk> stream = client.streamChat(aiRequest);

            stream.doOnNext(chunk -> {
                try {
                    if (!chunk.isDone() && chunk.getContent() != null
                            && !chunk.getContent().isEmpty()) {
                        fullResponse.append(chunk.getContent());
                        emitter.send(SseEmitter.event()
                                .name("chunk")
                                .data(chunk.getContent()));
                    }
                    if (chunk.isDone()) {
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data("[DONE]"));
                    }
                } catch (IOException e) {
                    log.warn("SSE client disconnected mid-stream: {}", e.getMessage());
                }
            }).blockLast();

            // Save complete response in its own transaction
            saveMessage(conversation.getId(), "assistant",
                    fullResponse.toString(), null, null, modelId, false);

            updateLastMessageAt(conversation.getId());
            autoGenerateTitle(conversation, userContent);

            emitter.complete();

        } catch (Exception e) {
            log.error("Streaming failed for conversation {}: {}",
                    conversation.getId(), e.getMessage());

            // Save error message
            saveMessage(conversation.getId(), "assistant",
                    "Sorry, an error occurred: " + e.getMessage(),
                    null, null, modelId, true);

            // Send error event to frontend
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Generation failed: " + e.getMessage()));
            } catch (IOException ignored) {}

            // IMPORTANT: complete() not completeWithError()
            // completeWithError triggers Tomcat async error dispatch
            // which re-runs security filters without SecurityContext → Access Denied
            emitter.complete();
        }
    }

    // =========================================================
    // Private — DB helpers with explicit transaction management
    // =========================================================

    /**
     * Save a message in its own transaction.
     *
     * <p>REQUIRES_NEW creates a new transaction regardless of context.
     * This is critical for the async streaming path — the virtual thread
     * has no ambient transaction, so REQUIRED would fail.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Message saveMessage(
            UUID conversationId,
            String role,
            String content,
            Integer promptTokens,
            Integer completionTokens,
            String modelUsed,
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
     * Update last_message_at in its own transaction.
     * Same reason as saveMessage — must be REQUIRES_NEW for async path.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLastMessageAt(UUID conversationId) {
        conversationRepository.updateLastMessageAt(conversationId, Instant.now());
    }

    /**
     * Auto-title in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoGenerateTitle(Conversation conversation, String firstMessage) {
        if ("New Conversation".equals(conversation.getTitle())) {
            String title = firstMessage.length() > 60
                    ? firstMessage.substring(0, 57) + "..."
                    : firstMessage;
            conversationRepository.findById(conversation.getId()).ifPresent(c -> {
                c.setTitle(title);
                conversationRepository.save(c);
            });
        }
    }

    // =========================================================
    // Private — context builder
    // =========================================================

    private List<AiMessage> buildContextMessages(Conversation conversation, String newUserContent) {
        List<AiMessage> messages = new java.util.ArrayList<>();

        if (conversation.getSystemPrompt() != null
                && !conversation.getSystemPrompt().isBlank()) {
            messages.add(AiMessage.system(conversation.getSystemPrompt()));
        }

        List<Message> history = messageRepository.findRecentMessages(
                conversation.getId(), MAX_CONTEXT_MESSAGES);
        java.util.Collections.reverse(history);

        history.forEach(msg ->
                messages.add(new AiMessage(
                        AiRole.valueOf(msg.getRole().toUpperCase()),
                        msg.getContent())));

        messages.add(AiMessage.user(newUserContent));
        return messages;
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