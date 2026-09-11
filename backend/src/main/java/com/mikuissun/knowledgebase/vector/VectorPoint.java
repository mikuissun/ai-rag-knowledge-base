package com.mikuissun.knowledgebase.vector;

public record VectorPoint(Long chunkId, Long documentId, Long knowledgeBaseId,
                          Long userId, Integer chunkIndex, String content, float[] vector) {
}
