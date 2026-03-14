package com.genailab.document.extractor;

import java.io.InputStream;

/**
 * Extracts plain text from a document file.
 *
 * <p>Each implementation handles one file type.
 * The DocumentProcessingService picks the right extractor
 * based on the document's file type.
 *
 * <p>Implementations: PdfTextExtractor, DocxTextExtractor, TxtTextExtractor
 */
public interface TextExtractor {

    /**
     * Extract all text from the document stream.
     *
     * @param inputStream the document content — caller is responsible for closing
     * @return the full extracted text as a single string
     * @throws TextExtractionException if extraction fails
     */
    String extract(InputStream inputStream);

    /**
     * The file type this extractor handles.
     * Must match the fileType stored on the Document entity.
     * Examples: "pdf", "docx", "txt"
     */
    String getSupportedType();
}