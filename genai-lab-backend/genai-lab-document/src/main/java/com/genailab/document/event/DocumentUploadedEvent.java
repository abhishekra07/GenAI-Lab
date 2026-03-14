package com.genailab.document.event;

import java.util.UUID;

/**
 * Published when a document has been saved to the database.
 * Carries the modelId so the processing pipeline knows which
 * embedding model to use for this document.
 *
 * @param documentId the saved document's ID
 * @param modelId    optional model preference from the upload request;
 *                   null means use the system default model
 */
public record DocumentUploadedEvent(UUID documentId, String modelId) {
}