package com.genailab.api.document;

import com.genailab.document.dto.DocumentResponse;
import com.genailab.document.service.DocumentService;
import com.genailab.security.domain.User;
import com.genailab.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;
    private final StorageService storageService;

    /**
     * POST /api/v1/documents
     * Upload a document. Returns 202 Accepted — processing is async.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "modelId", required = false) String modelId,
            @AuthenticationPrincipal User user) {

        log.info("Document upload request from user {}: {}, model: {}",
                user.getId(), file.getOriginalFilename(), modelId);

        DocumentResponse response = documentService.upload(file, user.getId(), modelId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * GET /api/v1/documents
     * List all documents for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<DocumentResponse> page = documentService.listDocuments(user.getId(), pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * GET /api/v1/documents/{documentId}
     * Get document status and metadata.
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User user) {

        DocumentResponse response = documentService.getDocument(documentId, user.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/documents/{documentId}/file
     *
     * Stream the actual file content to the frontend.
     *
     * <p>Uses StreamingResponseBody to avoid loading the entire file into memory.
     * The file is piped directly from MinIO/local storage to the HTTP response.
     *
     * <p>Response headers:
     * - Content-Type: set to the correct MIME type (application/pdf, text/plain, etc.)
     * - Content-Disposition: inline — browser renders PDFs and text in-tab,
     *   rather than forcing a download. Frontend can override by using
     *   Content-Disposition: attachment if it wants a download button.
     */
    @GetMapping("/{documentId}/file")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable UUID documentId,
            @RequestParam(value = "disposition", defaultValue = "inline") String disposition,
            @AuthenticationPrincipal User user) {

        DocumentResponse doc = documentService.getDocument(documentId, user.getId());

        String storageKey = documentService.getStorageKey(documentId, user.getId());
        String contentType = storageService.getContentType(storageKey);
        String filename = doc.getOriginalFilename();

        // "inline" → browser renders in-tab (PDF viewer, text viewer)
        // "attachment" → forces download dialog
        String contentDisposition = disposition.equals("attachment")
                ? "attachment; filename=\"" + filename + "\""
                : "inline; filename=\"" + filename + "\"";

        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = storageService.retrieve(storageKey)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(body);
    }

    /**
     * DELETE /api/v1/documents/{documentId}
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal User user) {

        documentService.deleteDocument(documentId, user.getId());
        return ResponseEntity.noContent().build();
    }
}