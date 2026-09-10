package com.mikuissun.knowledgebase.document.parser;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class WordDocumentParser implements DocumentParser {
    @Override public boolean supports(String extension) { return "docx".equals(extension); }
    @Override
    public String parse(Path filePath) throws IOException {
        try (var input = Files.newInputStream(filePath); var document = new XWPFDocument(input)) {
            var text = new LimitedTextWriter();
            for (var header : document.getHeaderList()) append(header.getBodyElements(), text);
            append(document.getBodyElements(), text);
            for (var footer : document.getFooterList()) append(footer.getBodyElements(), text);
            return text.toString();
        }
    }
    private void append(List<IBodyElement> elements, LimitedTextWriter text) throws IOException {
        for (var element : elements) {
            if (element instanceof XWPFParagraph paragraph) {
                text.write(paragraph.getText());
                text.write("\n");
            } else if (element instanceof XWPFTable table) {
                for (var row : table.getRows()) {
                    for (var cell : row.getTableCells()) append(cell.getBodyElements(), text);
                }
            }
        }
    }
}
