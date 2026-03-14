package com.genailab.document.extractor;

import com.genailab.document.extractor.exception.TextExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Extracts text from PDF files using Apache PDFBox.
 *
 * <p>PDFBox handles most real-world PDFs well — text-based PDFs,
 * multi-column layouts, and embedded fonts. It does NOT handle
 * scanned PDFs (images of text) — those require OCR which is
 * outside our current scope.
 */
@Component
@Slf4j
public class PdfTextExtractor implements TextExtractor {

    @Override
    public String extract(InputStream inputStream) {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Sort by position ensures text is extracted in reading order
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.debug("Extracted {} characters from PDF ({} pages)",
                    text.length(), document.getNumberOfPages());
            return text;
        } catch (Exception e) {
            throw new TextExtractionException("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSupportedType() {
        return "pdf";
    }
}