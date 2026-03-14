package com.genailab.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single chunk in a streaming AI response.
 *
 * <p>When streaming, the model sends tokens as they are generated
 * rather than waiting for the full response. Each chunk contains
 * a small piece of text. The client renders them incrementally,
 * producing the "typing" effect seen in ChatGPT.
 *
 * <p>The final chunk has {@code done = true} and may contain
 * token usage information (some providers only send usage on the last chunk).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStreamChunk {

    /**
     * The text fragment for this chunk.
     */
    private String content;

    /**
     * True on the final chunk — signals the stream is complete.
     */
    private boolean done;

    private AiChatResponse.TokenUsage tokenUsage;

    // Factory methods for clean call sites
    public static AiStreamChunk of(String content) {
        return AiStreamChunk.builder().content(content).done(false).build();
    }

    public static AiStreamChunk done(AiChatResponse.TokenUsage usage) {
        return AiStreamChunk.builder().content("").done(true).tokenUsage(usage).build();
    }
}