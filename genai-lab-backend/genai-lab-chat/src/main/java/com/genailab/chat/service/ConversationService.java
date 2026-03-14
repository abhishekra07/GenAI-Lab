package com.genailab.chat.service;

import com.genailab.chat.domain.Conversation;
import com.genailab.chat.dto.ConversationResponse;
import com.genailab.chat.dto.CreateConversationRequest;
import com.genailab.chat.repository.ConversationRepository;
import com.genailab.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manages conversation lifecycle — create, list, delete.
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;

    @Transactional
    public ConversationResponse createConversation(
            CreateConversationRequest request, UUID userId) {

        Conversation conversation = Conversation.builder()
                .userId(userId)
                .title(request.getTitle() != null && !request.getTitle().isBlank()
                        ? request.getTitle() : "New Conversation")
                .modelId(request.getModelId())
                .systemPrompt(request.getSystemPrompt())
                .build();

        Conversation saved = conversationRepository.save(conversation);
        log.info("Created conversation {} for user {}", saved.getId(), userId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(UUID userId, Pageable pageable) {
        return conversationRepository
                .findByUserIdOrderByLastMessageAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId, UUID userId) {
        Conversation conversation = findOwnedConversation(conversationId, userId);
        return toResponse(conversation);
    }

    @Transactional
    public void deleteConversation(UUID conversationId, UUID userId) {
        long deleted = conversationRepository.deleteByIdAndUserId(conversationId, userId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Conversation", conversationId.toString());
        }
        log.info("Deleted conversation {} for user {}", conversationId, userId);
    }

    /**
     * Load a conversation and verify ownership in one query.
     * Throws if not found or if the conversation belongs to a different user.
     */
    public Conversation findOwnedConversation(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId.toString()));
    }

    private ConversationResponse toResponse(Conversation c) {
        return ConversationResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .modelId(c.getModelId())
                .systemPrompt(c.getSystemPrompt())
                .isPinned(c.isPinned())
                .lastMessageAt(c.getLastMessageAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}