package com.mikuissun.knowledgebase.vector;

import java.util.List;

public interface VectorStoreService {
    void ensureCollection();
    void replaceDocumentPoints(Long userId, Long knowledgeBaseId, Long documentId,
                               List<VectorPoint> points);
    void deleteDocumentPoints(Long userId, Long knowledgeBaseId, Long documentId);
    void deleteKnowledgeBasePoints(Long userId, Long knowledgeBaseId);
    List<VectorSearchResult> similaritySearch(Long userId, Long knowledgeBaseId,
                                              float[] queryVector, int topK);
    String collectionName();
    int dimension();
}
