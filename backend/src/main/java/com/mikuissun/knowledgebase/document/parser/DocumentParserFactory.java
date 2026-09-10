package com.mikuissun.knowledgebase.document.parser;

import com.mikuissun.knowledgebase.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DocumentParserFactory {
    private final List<DocumentParser> parsers;
    public DocumentParserFactory(List<DocumentParser> parsers) { this.parsers = List.copyOf(parsers); }
    public DocumentParser getParser(String extension) {
        return parsers.stream().filter(parser -> parser.supports(extension)).findFirst()
                .orElseThrow(() -> new BusinessException(400, "仅支持 PDF、DOCX、MD、TXT 文件"));
    }
}
