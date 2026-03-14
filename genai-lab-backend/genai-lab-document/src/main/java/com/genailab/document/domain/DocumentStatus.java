package com.genailab.document.domain;

/**
 * Processing lifecycle states for a document.
 *
 * <p>State transitions:
 * <pre>
 * UPLOADED → PROCESSING → READY
 *                      ↘ FAILED
 * </pre>
 *
 * <ul>
 *   <li>UPLOADED   — file received and stored, processing not yet started</li>
 *   <li>PROCESSING — text extraction and chunking in progress</li>
 *   <li>READY      — all chunks saved, document is queryable via RAG</li>
 *   <li>FAILED     — processing failed, error_message contains the reason</li>
 * </ul>
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}