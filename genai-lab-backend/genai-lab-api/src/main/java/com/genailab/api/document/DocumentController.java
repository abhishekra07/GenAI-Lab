package com.genailab.api.document;

import com.genailab.document.dto.DocumentResponse;
import com.genailab.document.service.DocumentService;
import com.genailab.security.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST controller for the Document Analyzer feature.
 *
 * <p>File uploads use multipart/form-data encoding — the standard
 * for HTTP file uploads. Spring's MultipartFile abstraction handles
 * the multipart parsing automatically.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Upload a document for processing.
     *
     * <p>Accepts multipart/form-data with a "file" part.
     * Returns 202 Accepted — the document is saved but processing
     * happens asynchronously. Poll GET /documents/{id} for status.
     *
     * <p>Example curl:
     * curl -X POST http://localhost:8080/api/v1/documents \
     *   -H "Authorization: Bearer {token}" \
     *   -F "file=@/path/to/document.pdf"
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) {

        log.info("Document upload request from user {}: {}",
                user.getId(), file.getOriginalFilename());

        DocumentResponse response = documentService.upload(file, user.getId());

        // 202 Accepted — not 201 Created — because processing is async.
        // The resource exists but is not yet in its final state.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<DocumentResponse> page = documentService.listDocuments(user.getId(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User user) {

        DocumentResponse response = documentService.getDocument(documentId, user.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User user) {

        documentService.deleteDocument(documentId, user.getId());
        return ResponseEntity.noContent().build();
    }
}