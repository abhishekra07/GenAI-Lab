package com.genailab.chat.service;

import com.genailab.chat.domain.Message;
import com.genailab.chat.repository.ConversationRepository;
import com.genailab.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles all DB writes for the chat streaming pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatPersistenceService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    /**
     * Save a message in a brand new transaction.
     *
     * <p>REQUIRES_NEW: always creates a new transaction, suspending any
     * existing one. This is what allows safe calls from async virtual threads
     * which have no ambient transaction.
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
     * Update conversation's last_message_at in a brand new transaction.
     *
     * <p>Uses @Modifying query — requires an active write transaction.
     * REQUIRES_NEW ensures one is always created.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLastMessageAt(UUID conversationId) {
        conversationRepository.updateLastMessageAt(conversationId, Instant.now());
    }

    /**
     * Auto-generate title in a brand new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoGenerateTitle(UUID conversationId, String currentTitle, String firstMessage) {
        if ("New Conversation".equals(currentTitle)) {
            String title = firstMessage.length() > 60
                    ? firstMessage.substring(0, 57) + "..."
                    : firstMessage;
            conversationRepository.findById(conversationId).ifPresent(c -> {
                c.setTitle(title);
                conversationRepository.save(c);
            });
        }
    }
}