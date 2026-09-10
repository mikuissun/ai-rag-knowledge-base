package com.mikuissun.knowledgebase.document.parser;

import java.io.IOException;
import java.nio.file.Path;

public interface DocumentParser {
    boolean supports(String extension);
    String parse(Path filePath) throws IOException;
}
