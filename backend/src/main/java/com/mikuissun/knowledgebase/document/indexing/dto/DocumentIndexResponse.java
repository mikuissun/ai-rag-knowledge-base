package com.mikuissun.knowledgebase.document.indexing.dto;

public record DocumentIndexResponse(Long documentId, int pointCount,
                                    String collection, String indexingStatus) {
}
