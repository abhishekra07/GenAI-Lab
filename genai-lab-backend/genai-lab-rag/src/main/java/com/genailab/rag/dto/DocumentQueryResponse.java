package com.genailab.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentQueryResponse {

    /** The AI-generated answer based on retrieved context. */
    private String answer;

    /** Which model generated the answer. */
    private String modelUsed;

    /** Number of chunks retrieved and used as context. */
    private int chunksUsed;

    /** Token usage for cost tracking. */
    private TokenUsage tokenUsage;

    /**
     * Source chunks used to generate the answer.
     * Lets the frontend show "based on these passages" citations.
     */
    private List<SourceChunk> sources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceChunk {
        private int chunkIndex;
        private double similarity;
        private String excerpt;   // first 200 chars of the chunk
    }
}