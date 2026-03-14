package com.genailab.document.event;

import java.util.UUID;

/**
 * Published when a document has been successfully saved to the database.
 *
 * <p>This event drives the async processing pipeline.
 * It is published inside the upload transaction and consumed by
 * {@link DocumentEventListener} only AFTER that transaction commits —
 * guaranteed by @TransactionalEventListener(phase = AFTER_COMMIT).
 *
 * <p>WHY an event instead of a direct method call?
 * A direct @Async call from inside @Transactional has a race condition:
 * the async thread starts before the transaction commits, so the document
 * is not yet visible in the DB when the background thread queries it.
 *
 * Spring's @TransactionalEventListener solves this cleanly — the event
 * is held until the transaction commits, then fired. No manual transaction
 * synchronization code needed anywhere.
 */
public record DocumentUploadedEvent(UUID documentId) {
}