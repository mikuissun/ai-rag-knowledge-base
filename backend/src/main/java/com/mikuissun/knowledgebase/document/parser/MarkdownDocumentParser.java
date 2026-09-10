package com.mikuissun.knowledgebase.document.parser;

import org.springframework.stereotype.Component;

@Component
public class MarkdownDocumentParser extends TextDocumentParser {
    @Override public boolean supports(String extension) { return "md".equals(extension); }
}
