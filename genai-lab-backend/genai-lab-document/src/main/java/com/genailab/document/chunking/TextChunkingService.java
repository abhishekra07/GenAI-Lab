package com.genailab.document.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted document text into overlapping chunks for RAG.
 *
 * <p>WHY chunk at all? LLMs have a context window limit. A 100-page
 * PDF might have 80,000 tokens — we can't send it all. Instead we:
 * <ol>
 *   <li>Split into small chunks (512 tokens each)</li>
 *   <li>Embed each chunk as a vector</li>
 *   <li>At query time: retrieve only the most relevant chunks</li>
 *   <li>Send those chunks as context to the LLM</li>
 * </ol>
 *
 * <p>WHY overlapping chunks?
 * A sentence at the boundary of two chunks would be split in half.
 * The overlap (50 chars by default) ensures boundary content appears
 * fully in at least one chunk, preserving semantic coherence.
 *
 * <p>We use character-based chunking rather than token-based because:
 * <ul>
 *   <li>Token counting requires a tokenizer (adds a dependency)</li>
 *   <li>Character count is a reliable proxy: ~4 chars per token for English</li>
 *   <li>Simple and predictable — easier to debug</li>
 * </ul>
 *
 * <p>512 tokens × 4 chars/token ≈ 2048 characters per chunk.
 */
@Service
@Slf4j
public class TextChunkingService {

    private final int chunkSize;
    private final int chunkOverlap;

    public TextChunkingService(
            @Value("${genailab.rag.chunking.chunk-size:512}") int chunkSize,
            @Value("${genailab.rag.chunking.chunk-overlap:50}") int chunkOverlap) {
        // Convert token counts to approximate character counts
        // 4 chars per token is a safe average for English text
        this.chunkSize = chunkSize * 4;
        this.chunkOverlap = chunkOverlap * 4;
    }

    /**
     * Split text into a list of overlapping chunks.
     *
     * <p>Each chunk is represented as a {@link TextChunk} containing
     * the content and its character offsets in the original text.
     * Offsets are stored in the DB for source attribution — showing
     * users exactly where in the document an answer came from.
     *
     * @param text the full extracted document text
     * @return ordered list of chunks
     */
    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Normalise whitespace — collapse multiple blank lines,
        // trim leading/trailing whitespace from the whole document
        String normalised = text.replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        List<TextChunk> chunks = new ArrayList<>();
        int position = 0;
        int index = 0;

        while (position < normalised.length()) {
            int end = Math.min(position + chunkSize, normalised.length());

            // Try to break at a sentence or paragraph boundary
            // rather than cutting mid-word
            if (end < normalised.length()) {
                end = findBreakPoint(normalised, position, end);
            }

            String chunkContent = normalised.substring(position, end).trim();

            if (!chunkContent.isBlank()) {
                chunks.add(TextChunk.builder()
                        .index(index++)
                        .content(chunkContent)
                        .startChar(position)
                        .endChar(end)
                        // Approximate token count: chars / 4
                        .tokenCount(chunkContent.length() / 4)
                        .build());
            }

            // Move forward by chunkSize minus overlap
            // This creates the sliding window with overlap
            int advance = chunkSize - chunkOverlap;
            position += advance;
        }

        log.debug("Chunked {} chars into {} chunks (size={}, overlap={})",
                normalised.length(), chunks.size(), chunkSize, chunkOverlap);

        return chunks;
    }

    /**
     * Find the best break point near the target end position.
     *
     * <p>Preference order:
     * <ol>
     *   <li>Double newline (paragraph boundary) — best break</li>
     *   <li>Single newline (line boundary)</li>
     *   <li>Sentence-ending punctuation followed by space</li>
     *   <li>Any space (word boundary)</li>
     *   <li>Hard cut at target (fallback — happens for very long words)</li>
     * </ol>
     */
    private int findBreakPoint(String text, int start, int targetEnd) {
        // Search window: look back up to 20% of chunk size for a good break
        int searchStart = Math.max(start, targetEnd - chunkSize / 5);

        // Prefer paragraph break
        int idx = text.lastIndexOf("\n\n", targetEnd);
        if (idx >= searchStart) return idx + 2;

        // Then line break
        idx = text.lastIndexOf('\n', targetEnd);
        if (idx >= searchStart) return idx + 1;

        // Then sentence end
        for (int i = targetEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') &&
                    i + 1 < text.length() && text.charAt(i + 1) == ' ') {
                return i + 1;
            }
        }

        // Then word break
        idx = text.lastIndexOf(' ', targetEnd);
        if (idx >= searchStart) return idx + 1;

        // Hard cut fallback
        return targetEnd;
    }

    /**
     * Represents a single text chunk with position metadata.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TextChunk {
        private int index;
        private String content;
        private int startChar;
        private int endChar;
        private int tokenCount;
    }
}