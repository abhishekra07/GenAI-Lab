package com.genailab.api.chat;

import com.genailab.chat.dto.*;
import com.genailab.chat.service.ChatService;
import com.genailab.chat.service.ConversationService;
import com.genailab.security.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the AI Chat feature.
 *
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ConversationService conversationService;
    private final ChatService chatService;

    // =========================================================
    // Conversation endpoints
    // =========================================================

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal User user) {

        ConversationResponse response = conversationService
                .createConversation(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ConversationResponse>> listConversations(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ConversationResponse> page = conversationService
                .listConversations(user.getId(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> getConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal User user) {

        ConversationResponse response = conversationService
                .getConversation(conversationId, user.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal User user) {

        conversationService.deleteConversation(conversationId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // Message endpoints
    // =========================================================

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal User user) {

        List<MessageResponse> messages = chatService
                .getMessages(conversationId, user.getId());
        return ResponseEntity.ok(messages);
    }

    /**
     * Send a message and stream the AI response via Server-Sent Events.
     *
     * <p>Returns MediaType.TEXT_EVENT_STREAM_VALUE — the SSE content type.
     * The browser keeps the connection open and receives events as they arrive.
     *
     * <p>SSE event format:
     * <pre>
     * event: chunk
     * data: Hello
     *
     * event: chunk
     * data: , how
     *
     * event: done
     * data: [DONE]
     * </pre>
     *
     * <p>If stream=false in the request, use the non-streaming endpoint below.
     */
    @PostMapping(
            value = "/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal User user) {

        log.info("Stream message request for conversation {} from user {}",
                conversationId, user.getId());

        return chatService.streamMessage(conversationId, request, user.getId());
    }

    /**
     * Send a message and return the full response at once (non-streaming).
     * Useful for integrations that don't support SSE.
     */
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal User user) {

        log.info("Send message request for conversation {} from user {}",
                conversationId, user.getId());

        MessageResponse response = chatService
                .sendMessage(conversationId, request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}