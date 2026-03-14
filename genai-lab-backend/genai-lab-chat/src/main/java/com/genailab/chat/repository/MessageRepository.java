package com.genailab.chat.repository;

import com.genailab.chat.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Load all messages for a conversation in chronological order.
     *
     * <p>WHY load all messages instead of paginating?
     * The AI needs the full conversation history to generate a contextually
     * aware response. We can't send "page 2" of a conversation to the model —
     * it needs the whole thread.
     *
     * <p>This is bounded by the model's context window. In practice,
     * very long conversations will hit the token limit before the message
     * count becomes a performance problem. We'll add a "last N messages"
     * cap later if needed.
     */
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * Load the most recent N messages for a conversation.
     * Used when building context for the AI — we cap history to avoid
     * exceeding the model's context window on very long conversations.
     */
    @Query(value = """
            SELECT * FROM messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Message> findRecentMessages(
            @Param("conversationId") UUID conversationId,
            @Param("limit") int limit);

    /**
     * Count messages in a conversation — used to auto-generate a title
     * from the first user message.
     */
    long countByConversationId(UUID conversationId);
}