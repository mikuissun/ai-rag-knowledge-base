package com.mikuissun.knowledgebase.search;

import com.mikuissun.knowledgebase.search.dto.KnowledgeSearchRequest;
import com.mikuissun.knowledgebase.search.dto.KnowledgeSearchResponse;

public interface KnowledgeSearchService {
    KnowledgeSearchResponse search(Long knowledgeBaseId, Long userId, KnowledgeSearchRequest request);
}
