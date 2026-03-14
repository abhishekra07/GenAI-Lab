package com.genailab.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from a non-streaming AI chat call.
 *
 * <p>Contains the generated text plus token usage metadata.
 * Token counts come directly from the API response and are
 * stored in the messages table for cost tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    private String content;

    private String modelUsed;

    private TokenUsage tokenUsage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {

        private int promptTokens;

        private int completionTokens;

        private int totalTokens;
    }
}