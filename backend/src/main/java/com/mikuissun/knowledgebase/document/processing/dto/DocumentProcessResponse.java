package com.mikuissun.knowledgebase.document.processing.dto;

public record DocumentProcessResponse(Long documentId, int chunkCount, int embeddingCount,
        String embeddingModel, String processingStatus) {
}
