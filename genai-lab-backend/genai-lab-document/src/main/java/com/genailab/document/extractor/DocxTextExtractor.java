package com.genailab.document.extractor;

import com.genailab.document.extractor.exception.TextExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Extracts text from DOCX files using Apache POI.
 *
 * <p>XWPFWordExtractor handles the Open XML format used by
 * Word 2007 and later (.docx). It extracts body text, headers,
 * footers, and text boxes in document order.
 *
 * <p>Does not support the older binary .doc format.
 */
@Component
@Slf4j
public class DocxTextExtractor implements TextExtractor {

    @Override
    public String extract(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            String text = extractor.getText();
            log.debug("Extracted {} characters from DOCX", text.length());
            return text;

        } catch (Exception e) {
            throw new TextExtractionException(
                    "Failed to extract text from DOCX: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSupportedType() {
        return "docx";
    }
}