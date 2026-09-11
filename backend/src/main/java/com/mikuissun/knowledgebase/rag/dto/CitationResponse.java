package com.mikuissun.knowledgebase.rag.dto;

public record CitationResponse(Long documentId, String documentName, Long chunkId,
                               Integer chunkIndex, float score, String contentSnippet) {
}
