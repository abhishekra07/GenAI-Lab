package com.genailab.document.extractor;

import com.genailab.document.extractor.exception.TextExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Extracts text from PDF files using Apache PDFBox 3.x.
 *
 * <p>PDFBox handles text-based PDFs well. It does NOT handle
 * scanned PDFs (images of text) — those require OCR which is
 * outside our current scope.
 *
 * <p>Note: Loader.loadPDF() requires a byte array or RandomAccessRead,
 * not a raw InputStream. We read all bytes first, which is acceptable
 * since we already have the file in memory from the multipart upload.
 */
@Component
@Slf4j
public class PdfTextExtractor implements TextExtractor {

    @Override
    public String extract(InputStream inputStream) {
        try {
            // PDFBox 3.x Loader requires a byte array — read all bytes upfront.
            // The file is already in memory from the multipart upload,
            // so this does not introduce an additional memory cost.
            byte[] bytes = inputStream.readAllBytes();

            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                // Sort by position ensures text is extracted in reading order
                stripper.setSortByPosition(true);
                String text = stripper.getText(document);
                log.debug("Extracted {} characters from PDF ({} pages)",
                        text.length(), document.getNumberOfPages());
                return text;
            }
        } catch (Exception e) {
            throw new TextExtractionException(
                    "Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSupportedType() {
        return "pdf";
    }
}