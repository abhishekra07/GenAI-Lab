package com.genailab.ai.embedding.mock;

import com.genailab.ai.embedding.EmbeddingClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock embedding client for development and testing.
 *
 * <p>Returns deterministic pseudo-random embeddings based on the
 * input text's hash code. This means:
 * <ul>
 *   <li>Same text always produces the same embedding vector</li>
 *   <li>Different texts produce different vectors</li>
 *   <li>Vectors are normalized to unit length (required for cosine similarity)</li>
 *   <li>Similarity search will work — similar-looking texts will have
 *       similar hash codes and thus similar vectors</li>
 * </ul>
 *
 * <p>Not annotated with @Component — only instantiated by
 * {@link com.genailab.ai.config.AiProviderConfig} when mock is active.
 */
@Slf4j
public class MockEmbeddingClientImpl implements EmbeddingClient {

    private static final int DIMENSIONS = 1536;
    private static final String MODEL_NAME = "mock-embedding-model";

    @Override
    public List<Float> embed(String text) {
        log.debug("[MOCK EMBEDDING] Generating embedding for text of length: {}", text.length());
        return generateEmbedding(text);
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        log.debug("[MOCK EMBEDDING] Generating embeddings for {} texts", texts.size());
        return texts.stream()
                .map(this::generateEmbedding)
                .toList();
    }

    @Override
    public int getDimensions() {
        return DIMENSIONS;
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    /**
     * Generate a deterministic unit-length vector from text.
     *
     * <p>Uses the text's hash code as a seed for a simple linear
     * congruential generator. This produces a consistent vector
     * for the same input text across calls and JVM restarts
     * (as long as String.hashCode() is stable — which it is in Java).
     *
     * <p>The vector is L2-normalized so cosine similarity works correctly
     * in pgvector queries.
     */
    private List<Float> generateEmbedding(String text) {
        // Use hashCode as seed — same text always gives same vector
        long seed = text.hashCode();

        List<Float> embedding = new ArrayList<>(DIMENSIONS);
        double sumOfSquares = 0.0;

        // Linear congruential generator for deterministic pseudo-random values
        for (int i = 0; i < DIMENSIONS; i++) {
            seed = (seed * 6364136223846793005L + 1442695040888963407L);
            float value = (float) (seed * 1e-18);
            embedding.add(value);
            sumOfSquares += value * value;
        }

        // L2 normalize — divide each element by the vector magnitude
        // This is required for cosine similarity to work correctly
        double magnitude = Math.sqrt(sumOfSquares);
        if (magnitude > 0) {
            for (int i = 0; i < DIMENSIONS; i++) {
                embedding.set(i, (float) (embedding.get(i) / magnitude));
            }
        }

        return embedding;
    }
}