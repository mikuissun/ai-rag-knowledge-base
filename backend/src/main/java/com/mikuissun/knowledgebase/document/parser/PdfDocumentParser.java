package com.mikuissun.knowledgebase.document.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Path;

@Component
public class PdfDocumentParser implements DocumentParser {
    @Override public boolean supports(String extension) { return "pdf".equals(extension); }
    @Override
    public String parse(Path filePath) throws IOException {
        try (var document = Loader.loadPDF(filePath.toFile())) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) {
                throw new IOException("Encrypted or restricted PDF");
            }
            var text = new LimitedTextWriter();
            new PDFTextStripper().writeText(document, text);
            return text.toString();
        }
    }
}
