package com.genailab.document.extractor;

import com.genailab.document.extractor.exception.TextExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Extracts text from plain text files.
 *
 * <p>Reads the entire file as UTF-8. We assume UTF-8 as it covers
 * the vast majority of text files. Non-UTF-8 files will have
 * replacement characters for unmappable bytes rather than failing.
 */
@Component
@Slf4j
public class TxtTextExtractor implements TextExtractor {

    @Override
    public String extract(InputStream inputStream) {
        try {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Extracted {} characters from TXT", text.length());
            return text;
        } catch (IOException e) {
            throw new TextExtractionException(
                    "Failed to read text file: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSupportedType() {
        return "txt";
    }
}