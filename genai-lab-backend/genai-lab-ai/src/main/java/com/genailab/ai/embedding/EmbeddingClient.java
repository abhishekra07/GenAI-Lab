package com.genailab.ai.embedding;

import java.util.List;

/**
 * Core abstraction for text embedding generation.
 *
 * <p>Used by the RAG pipeline to:
 * <ul>
 *   <li>Embed document chunks during ingestion (stored in document_embeddings)</li>
 *   <li>Embed user queries at search time (compared against stored embeddings)</li>
 * </ul>
 *
 * <p>The dimension of the returned vectors must match the vector(N)
 * column type in the database. For text-embedding-3-small: N=1536.
 */
public interface EmbeddingClient {

    /**
     * Generate an embedding vector for a single text input.
     *
     * @param text the input text to embed
     * @return a list of floats representing the embedding vector
     */
    List<Float> embed(String text);

    /**
     * Generate embedding vectors for multiple texts in a single API call.
     *
     * <p>Batch embedding is significantly more efficient than calling
     * {@link #embed} in a loop — it reduces API round-trips and often
     * gets better throughput pricing.
     *
     * <p>The returned list preserves input order:
     * result.get(0) is the embedding for texts.get(0), etc.
     *
     * @param texts the list of texts to embed
     * @return a list of embedding vectors, one per input text
     */
    List<List<Float>> embedAll(List<String> texts);

    /**
     * The dimensionality of vectors produced by this client.
     * Must match the vector(N) column in document_embeddings.
     */
    int getDimensions();

    /**
     * The model name used for embedding.
     * Stored in document_embeddings.embedding_model for traceability.
     */
    String getModelName();
}