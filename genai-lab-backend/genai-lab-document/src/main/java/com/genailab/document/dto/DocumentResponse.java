package com.genailab.document.dto;

import com.genailab.document.domain.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    private UUID id;
    private String originalFilename;
    private String fileType;
    private long fileSizeBytes;
    private DocumentStatus status;
    private Integer pageCount;
    private String errorMessage;
    private Instant processedAt;
    private Instant createdAt;
    private Instant updatedAt;
}