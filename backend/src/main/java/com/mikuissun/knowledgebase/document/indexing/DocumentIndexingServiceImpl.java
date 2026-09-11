package com.mikuissun.knowledgebase.document.indexing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.document.entity.ChunkEmbedding;
import com.mikuissun.knowledgebase.document.entity.Document;
import com.mikuissun.knowledgebase.document.entity.DocumentChunk;
import com.mikuissun.knowledgebase.document.indexing.dto.DocumentIndexResponse;
import com.mikuissun.knowledgebase.document.mapper.ChunkEmbeddingMapper;
import com.mikuissun.knowledgebase.document.mapper.DocumentChunkMapper;
import com.mikuissun.knowledgebase.document.mapper.DocumentMapper;
import com.mikuissun.knowledgebase.document.processing.DocumentProcessingStatus;
import com.mikuissun.knowledgebase.knowledge.service.KnowledgeBaseService;
import com.mikuissun.knowledgebase.vector.VectorPoint;
import com.mikuissun.knowledgebase.vector.VectorStoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentIndexingServiceImpl implements DocumentIndexingService {
    private final DocumentMapper documents;
    private final DocumentChunkMapper chunks;
    private final ChunkEmbeddingMapper embeddings;
    private final KnowledgeBaseService knowledgeBases;
    private final VectorStoreService vectorStore;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public DocumentIndexingServiceImpl(DocumentMapper documents, DocumentChunkMapper chunks,
            ChunkEmbeddingMapper embeddings, KnowledgeBaseService knowledgeBases,
            VectorStoreService vectorStore, ObjectMapper objectMapper, PlatformTransactionManager manager) {
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.knowledgeBases = knowledgeBases;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public DocumentIndexResponse index(Long knowledgeBaseId, Long documentId, Long userId) {
        knowledgeBases.getByIdAndUserId(knowledgeBaseId, userId);
        Document document = findOwnedDocument(knowledgeBaseId, documentId, userId);
        if (!DocumentProcessingStatus.PROCESSED.equals(document.getProcessingStatus())) {
            throw new BusinessException(409, "请先完成文档文本切分和 Embedding");
        }
        acquireIndexing(knowledgeBaseId, documentId, userId);
        try {
            List<VectorPoint> points = loadPoints(knowledgeBaseId, documentId, userId);
            vectorStore.replaceDocumentPoints(userId, knowledgeBaseId, documentId, points);
            markIndexed(knowledgeBaseId, documentId, userId);
            return new DocumentIndexResponse(documentId, points.size(), vectorStore.collectionName(),
                    DocumentIndexingStatus.INDEXED);
        } catch (RuntimeException exception) {
            markFailed(knowledgeBaseId, documentId, userId, publicError(exception));
            throw exception;
        }
    }

    private void acquireIndexing(Long knowledgeBaseId, Long documentId, Long userId) {
        Integer updated = transactions.execute(status -> documents.update(null,
                new LambdaUpdateWrapper<Document>()
                        .eq(Document::getId, documentId)
                        .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(Document::getUserId, userId)
                        .eq(Document::getProcessingStatus, DocumentProcessingStatus.PROCESSED)
                        .ne(Document::getIndexingStatus, DocumentIndexingStatus.INDEXING)
                        .set(Document::getIndexingStatus, DocumentIndexingStatus.INDEXING)
                        .set(Document::getIndexingError, null)));
        if (updated == null || updated != 1) {
            Document current = findOwnedDocument(knowledgeBaseId, documentId, userId);
            if (DocumentIndexingStatus.INDEXING.equals(current.getIndexingStatus())) {
                throw new BusinessException(409, "文档正在建立向量索引，请勿重复提交");
            }
            throw new BusinessException(409, "文档处理状态发生变化，请重试");
        }
    }

    private List<VectorPoint> loadPoints(Long knowledgeBaseId, Long documentId, Long userId) {
        List<DocumentChunk> documentChunks = chunks.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId)
                .eq(DocumentChunk::getKnowledgeBaseId, knowledgeBaseId)
                .eq(DocumentChunk::getUserId, userId)
                .orderByAsc(DocumentChunk::getChunkIndex));
        if (documentChunks.isEmpty()) {
            throw new BusinessException(409, "文档没有可索引的文本块，请重新处理");
        }

        List<Long> chunkIds = documentChunks.stream().map(DocumentChunk::getId).toList();
        List<ChunkEmbedding> storedEmbeddings = embeddings.selectList(
                new LambdaQueryWrapper<ChunkEmbedding>().in(ChunkEmbedding::getChunkId, chunkIds));
        Map<Long, ChunkEmbedding> byChunk = new HashMap<>();
        for (ChunkEmbedding embedding : storedEmbeddings) byChunk.put(embedding.getChunkId(), embedding);
        if (byChunk.size() != documentChunks.size()) {
            throw new BusinessException(409, "部分文本块缺少 Embedding，请重新处理文档");
        }

        return documentChunks.stream().map(chunk -> toPoint(chunk, byChunk.get(chunk.getId()),
                knowledgeBaseId, documentId, userId)).toList();
    }

    private VectorPoint toPoint(DocumentChunk chunk, ChunkEmbedding embedding,
            Long knowledgeBaseId, Long documentId, Long userId) {
        if (embedding == null || embedding.getDimension() == null
                || embedding.getDimension() != vectorStore.dimension()) {
            throw new BusinessException(409, "文本块 Embedding 维度与 Qdrant collection 不一致");
        }
        float[] vector = parseVector(embedding.getEmbedding());
        if (vector.length != vectorStore.dimension()) {
            throw new BusinessException(409, "文本块 Embedding 数据维度不正确");
        }
        return new VectorPoint(chunk.getId(), documentId, knowledgeBaseId, userId,
                chunk.getChunkIndex(), chunk.getContent(), vector);
    }

    private float[] parseVector(String json) {
        try {
            float[] vector = objectMapper.readValue(json, float[].class);
            for (float value : vector) {
                if (!Float.isFinite(value)) throw new BusinessException(409, "Embedding 包含非法数值");
            }
            return vector;
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new BusinessException(409, "Embedding 数据无法解析，请重新处理文档");
        }
    }

    private Document findOwnedDocument(Long knowledgeBaseId, Long documentId, Long userId) {
        Document document = documents.selectOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getId, documentId)
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .eq(Document::getUserId, userId));
        if (document == null) throw new BusinessException(404, "文档不存在");
        return document;
    }

    private void markIndexed(Long knowledgeBaseId, Long documentId, Long userId) {
        Integer updated = transactions.execute(status -> documents.update(null,
                new LambdaUpdateWrapper<Document>()
                        .eq(Document::getId, documentId)
                        .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(Document::getUserId, userId)
                        .eq(Document::getIndexingStatus, DocumentIndexingStatus.INDEXING)
                        .set(Document::getIndexingStatus, DocumentIndexingStatus.INDEXED)
                        .set(Document::getIndexingError, null)
                        .set(Document::getIndexedAt, LocalDateTime.now())));
        if (updated == null || updated != 1) throw new IllegalStateException("Document index status update failed");
    }

    private void markFailed(Long knowledgeBaseId, Long documentId, Long userId, String message) {
        try {
            transactions.executeWithoutResult(status -> documents.update(null,
                    new LambdaUpdateWrapper<Document>()
                            .eq(Document::getId, documentId)
                            .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                            .eq(Document::getUserId, userId)
                            .eq(Document::getIndexingStatus, DocumentIndexingStatus.INDEXING)
                            .set(Document::getIndexingStatus, DocumentIndexingStatus.FAILED)
                            .set(Document::getIndexingError, message)));
        } catch (RuntimeException ignored) {
            // Preserve the original indexing exception.
        }
    }

    private String publicError(RuntimeException exception) {
        String message = exception instanceof BusinessException ? exception.getMessage() : "向量索引失败";
        if (message == null || message.isBlank()) return "向量索引失败";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
