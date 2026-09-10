package com.mikuissun.knowledgebase.document.processing;

import com.mikuissun.knowledgebase.document.processing.dto.DocumentProcessResponse;

public interface DocumentProcessingService {
    DocumentProcessResponse process(Long knowledgeBaseId, Long documentId, Long userId);
}
