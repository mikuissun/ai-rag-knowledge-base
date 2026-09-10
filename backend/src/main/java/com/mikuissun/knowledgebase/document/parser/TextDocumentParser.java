package com.mikuissun.knowledgebase.document.parser;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TextDocumentParser implements DocumentParser {
    @Override public boolean supports(String extension) { return "txt".equals(extension); }
    @Override
    public String parse(Path filePath) throws IOException {
        try (var reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            var text = new LimitedTextWriter();
            reader.transferTo(text);
            String result = text.toString();
            if (result.indexOf('\0') >= 0) throw new IOException("Binary text file");
            return result.startsWith("\uFEFF") ? result.substring(1) : result;
        }
    }
}
