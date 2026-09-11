package com.mikuissun.knowledgebase.search.dto;

import com.mikuissun.knowledgebase.vector.VectorSearchResult;

public record KnowledgeSearchResultResponse(Long chunkId, Long documentId, Integer chunkIndex,
                                            String content, float score) {
    public static KnowledgeSearchResultResponse from(VectorSearchResult result) {
        return new KnowledgeSearchResultResponse(result.chunkId(), result.documentId(),
                result.chunkIndex(), result.content(), result.score());
    }
}
