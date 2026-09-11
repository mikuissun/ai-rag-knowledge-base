package com.mikuissun.knowledgebase.search;

import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.document.embedding.EmbeddingService;
import com.mikuissun.knowledgebase.knowledge.service.KnowledgeBaseService;
import com.mikuissun.knowledgebase.search.dto.KnowledgeSearchRequest;
import com.mikuissun.knowledgebase.search.dto.KnowledgeSearchResponse;
import com.mikuissun.knowledgebase.search.dto.KnowledgeSearchResultResponse;
import com.mikuissun.knowledgebase.vector.QdrantProperties;
import com.mikuissun.knowledgebase.vector.VectorSearchResult;
import com.mikuissun.knowledgebase.vector.VectorStoreService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {
    private final KnowledgeBaseService knowledgeBases;
    private final EmbeddingService embeddings;
    private final VectorStoreService vectorStore;
    private final QdrantProperties properties;

    public KnowledgeSearchServiceImpl(KnowledgeBaseService knowledgeBases, EmbeddingService embeddings,
            VectorStoreService vectorStore, QdrantProperties properties) {
        this.knowledgeBases = knowledgeBases;
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @Override
    public KnowledgeSearchResponse search(Long knowledgeBaseId, Long userId, KnowledgeSearchRequest request) {
        knowledgeBases.getByIdAndUserId(knowledgeBaseId, userId);
        String query = request.query().trim();
        int topK = request.topK() == null ? properties.getSearchTopK() : request.topK();
        if (topK < 1 || topK > properties.getMaxSearchTopK()) {
            throw new BusinessException(400, "topK 必须在 1 到 " + properties.getMaxSearchTopK() + " 之间");
        }
        List<float[]> vectors = embeddings.embedBatch(List.of(query));
        if (vectors == null || vectors.size() != 1 || vectors.get(0) == null
                || vectors.get(0).length != vectorStore.dimension()) {
            throw new BusinessException(502, "查询 Embedding 返回数量或维度不正确");
        }
        List<VectorSearchResult> results = vectorStore.similaritySearch(
                userId, knowledgeBaseId, vectors.get(0), topK);
        return new KnowledgeSearchResponse(knowledgeBaseId, query, topK,
                results.stream().map(KnowledgeSearchResultResponse::from).toList());
    }
}
