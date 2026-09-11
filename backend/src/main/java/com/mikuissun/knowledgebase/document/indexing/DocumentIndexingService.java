package com.mikuissun.knowledgebase.document.indexing;

import com.mikuissun.knowledgebase.document.indexing.dto.DocumentIndexResponse;

public interface DocumentIndexingService {
    DocumentIndexResponse index(Long knowledgeBaseId, Long documentId, Long userId);
}
