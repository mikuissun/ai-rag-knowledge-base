package com.mikuissun.knowledgebase.search.dto;

import java.util.List;

public record KnowledgeSearchResponse(Long knowledgeBaseId, String query, int topK,
                                      List<KnowledgeSearchResultResponse> results) {
}
