package com.genailab.document.event;

import com.genailab.document.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for document domain events and triggers the processing pipeline.
 *
 * <p>@TransactionalEventListener(phase = AFTER_COMMIT) is the key annotation here.
 * It tells Spring:
 * <ul>
 *   <li>Wait until the transaction that published this event has committed</li>
 *   <li>Only then invoke this method</li>
 *   <li>If the transaction rolls back — do NOT invoke this method</li>
 * </ul>
 *
 * <p>This eliminates the race condition completely:
 * by the time this listener fires, the document INSERT is committed
 * and fully visible to all database connections including the async thread.
 *
 * <p>@Async here runs the processing on a separate thread so the HTTP
 * response is not blocked waiting for document processing to complete.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentEventListener {

    private final DocumentProcessingService documentProcessingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        log.debug("Document upload committed — triggering processing for: {}, model: {}",
                event.documentId(), event.modelId());
        documentProcessingService.processAsync(event.documentId(), event.modelId());
    }
}