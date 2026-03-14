package com.genailab.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single message in an AI conversation.
 *
 * <p>This is our internal representation — independent of any AI provider.
 * When calling OpenAI, we convert these to Spring AI's Message types.
 * When a different provider is added, we convert to their types instead.
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {

    private AiRole role;
    private String content;

    // Factory methods for convenience — clean call sites
    public static AiMessage user(String content) {
        return new AiMessage(AiRole.USER, content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(AiRole.ASSISTANT, content);
    }

    public static AiMessage system(String content) {
        return new AiMessage(AiRole.SYSTEM, content);
    }
}