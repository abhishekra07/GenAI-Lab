package com.genailab.api.rag;

import com.genailab.rag.dto.DocumentQueryRequest;
import com.genailab.rag.dto.DocumentQueryResponse;
import com.genailab.rag.service.RagService;
import com.genailab.security.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rag/documents")
@RequiredArgsConstructor
@Slf4j
public class RagController {

    private final RagService ragService;

    @PostMapping("/{documentId}/query")
    public ResponseEntity<DocumentQueryResponse> query(@PathVariable UUID documentId, @Valid @RequestBody DocumentQueryRequest request,
            @AuthenticationPrincipal User user) {
        log.info("Document query request: document={}, user={}", documentId, user.getId());
        DocumentQueryResponse response = ragService.query(documentId, request, user.getId());
        return ResponseEntity.ok(response);
    }
}