package com.genailab.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for AI chat completion.
 *
 * <p>Design note on {@code messages}:
 * We send the full conversation history on every request. This is how
 * all LLM APIs work — they are stateless, so context must be resent
 * each time. The chat module builds this list from stored messages
 * before calling the AI layer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    /**
     * The model identifier — matches model_key in ai_model_configs table.
     * Example: "gpt-4o-mini", "gpt-4o"
     */
    private String modelId;

    /**
     * Full conversation history in chronological order.
     */
    private List<AiMessage> messages;

    /**
     * Controls randomness. Range 0.0–2.0.
     * 0.0 = deterministic, 1.0 = default, 2.0 = very random.
     */
    private Double temperature;

    /**
     * Maximum tokens to generate in the response.
     */
    private Integer maxTokens;

    /**
     * Whether to stream the response chunk by chunk.
     * When true, use streamChat(). When false, use chat().
     */
    @Builder.Default
    private boolean stream = false;
}