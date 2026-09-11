package com.mikuissun.knowledgebase.vector;

public record VectorSearchResult(Long chunkId, Long documentId, Integer chunkIndex,
                                 String content, float score) {
}
