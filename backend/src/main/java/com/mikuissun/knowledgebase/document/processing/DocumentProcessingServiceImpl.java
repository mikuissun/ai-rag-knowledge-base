package com.mikuissun.knowledgebase.document.processing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.document.chunk.TextChunk;
import com.mikuissun.knowledgebase.document.chunk.TextChunker;
import com.mikuissun.knowledgebase.document.embedding.EmbeddingService;
import com.mikuissun.knowledgebase.document.entity.*;
import com.mikuissun.knowledgebase.document.mapper.*;
import com.mikuissun.knowledgebase.document.processing.dto.DocumentProcessResponse;
import com.mikuissun.knowledgebase.document.indexing.DocumentIndexingStatus;
import com.mikuissun.knowledgebase.knowledge.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentProcessingServiceImpl implements DocumentProcessingService {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingServiceImpl.class);
    private final DocumentMapper documents;
    private final DocumentChunkMapper chunks;
    private final ChunkEmbeddingMapper embeddings;
    private final KnowledgeBaseService knowledgeBases;
    private final TextChunker chunker;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public DocumentProcessingServiceImpl(DocumentMapper documents, DocumentChunkMapper chunks,
            ChunkEmbeddingMapper embeddings, KnowledgeBaseService knowledgeBases, TextChunker chunker,
            EmbeddingService embeddingService, ObjectMapper objectMapper, PlatformTransactionManager manager) {
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.knowledgeBases = knowledgeBases;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public DocumentProcessResponse process(Long knowledgeBaseId, Long documentId, Long userId) {
        knowledgeBases.getByIdAndUserId(knowledgeBaseId, userId);
        Document document = findOwnedDocument(knowledgeBaseId, documentId, userId, false);
        acquireProcessing(knowledgeBaseId, documentId, userId);
        try {
            List<TextChunk> generatedChunks = chunker.chunk(document.getContentText());
            if (generatedChunks.isEmpty()) {
                throw new BusinessException(400, "文档正文为空，无法处理");
            }
            List<float[]> vectors = embeddingService.embedBatch(
                    generatedChunks.stream().map(TextChunk::content).toList());
            validateVectors(generatedChunks, vectors);
            return saveReplacement(knowledgeBaseId, documentId, userId, generatedChunks, vectors);
        } catch (RuntimeException exception) {
            markFailed(knowledgeBaseId, documentId, userId, publicError(exception));
            throw exception;
        }
    }

    private void acquireProcessing(Long knowledgeBaseId, Long documentId, Long userId) {
        Integer updated = transactions.execute(status -> documents.update(null,
                new LambdaUpdateWrapper<Document>()
                        .eq(Document::getId, documentId)
                        .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(Document::getUserId, userId)
                        .ne(Document::getProcessingStatus, DocumentProcessingStatus.PROCESSING)
                        .ne(Document::getIndexingStatus, DocumentIndexingStatus.INDEXING)
                        .set(Document::getProcessingStatus, DocumentProcessingStatus.PROCESSING)
                        .set(Document::getProcessingError, null)
                        .set(Document::getProcessedAt, null)));
        if (updated == null || updated != 1) {
            Document current = findOwnedDocument(knowledgeBaseId, documentId, userId, false);
            if (DocumentProcessingStatus.PROCESSING.equals(current.getProcessingStatus())) {
                throw new BusinessException(409, "文档正在处理中，请勿重复提交");
            }
            if (DocumentIndexingStatus.INDEXING.equals(current.getIndexingStatus())) {
                throw new BusinessException(409, "文档正在建立向量索引，请稍后再重新处理");
            }
            throw new BusinessException(409, "文档处理状态发生变化，请重试");
        }
    }

    private DocumentProcessResponse saveReplacement(Long knowledgeBaseId, Long documentId, Long userId,
            List<TextChunk> generatedChunks, List<float[]> vectors) {
        return transactions.execute(status -> {
            Document current = findOwnedDocument(knowledgeBaseId, documentId, userId, true);
            if (!DocumentProcessingStatus.PROCESSING.equals(current.getProcessingStatus())) {
                throw new BusinessException(409, "文档处理状态发生变化，请重试");
            }

            chunks.delete(new LambdaQueryWrapper<DocumentChunk>()
                    .eq(DocumentChunk::getDocumentId, documentId)
                    .eq(DocumentChunk::getKnowledgeBaseId, knowledgeBaseId)
                    .eq(DocumentChunk::getUserId, userId));

            for (int index = 0; index < generatedChunks.size(); index++) {
                TextChunk source = generatedChunks.get(index);
                DocumentChunk chunk = new DocumentChunk();
                chunk.setUserId(userId);
                chunk.setKnowledgeBaseId(knowledgeBaseId);
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(source.index());
                chunk.setContent(source.content());
                chunk.setCharCount(source.charCount());
                chunk.setTokenEstimate(source.tokenEstimate());
                chunk.setEmbeddingStatus("COMPLETED");
                if (chunks.insert(chunk) != 1) throw new IllegalStateException("Chunk insert failed");

                ChunkEmbedding embedding = new ChunkEmbedding();
                embedding.setChunkId(chunk.getId());
                embedding.setModelName(embeddingService.modelName());
                embedding.setDimension(embeddingService.dimension());
                embedding.setEmbedding(toJson(vectors.get(index)));
                if (embeddings.insert(embedding) != 1) {
                    throw new IllegalStateException("Embedding insert failed");
                }
            }

            int updated = documents.update(null, new LambdaUpdateWrapper<Document>()
                    .eq(Document::getId, documentId)
                    .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                    .eq(Document::getUserId, userId)
                    .eq(Document::getProcessingStatus, DocumentProcessingStatus.PROCESSING)
                    .set(Document::getProcessingStatus, DocumentProcessingStatus.PROCESSED)
                    .set(Document::getProcessingError, null)
                    .set(Document::getProcessedAt, LocalDateTime.now())
                    .set(Document::getIndexingStatus, DocumentIndexingStatus.PENDING)
                    .set(Document::getIndexingError, null));
            if (updated != 1) throw new IllegalStateException("Document status update failed");

            return new DocumentProcessResponse(documentId, generatedChunks.size(), vectors.size(),
                    embeddingService.modelName(), DocumentProcessingStatus.PROCESSED);
        });
    }

    private Document findOwnedDocument(Long knowledgeBaseId, Long documentId, Long userId, boolean lock) {
        LambdaQueryWrapper<Document> query = new LambdaQueryWrapper<Document>()
                .eq(Document::getId, documentId)
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .eq(Document::getUserId, userId);
        if (lock) query.last("FOR UPDATE");
        Document document = documents.selectOne(query);
        if (document == null) throw new BusinessException(404, "文档不存在");
        return document;
    }

    private void validateVectors(List<TextChunk> generatedChunks, List<float[]> vectors) {
        if (vectors == null || vectors.size() != generatedChunks.size()) {
            throw new BusinessException(502, "Embedding 返回数量不正确");
        }
        for (float[] vector : vectors) {
            if (vector == null || vector.length != embeddingService.dimension()) {
                throw new BusinessException(502, "Embedding 返回维度不正确");
            }
            for (float value : vector) {
                if (!Float.isFinite(value)) throw new BusinessException(502, "Embedding 返回非法数值");
            }
        }
    }

    private String toJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embedding serialization failed", exception);
        }
    }

    private void markFailed(Long knowledgeBaseId, Long documentId, Long userId, String error) {
        try {
            transactions.executeWithoutResult(status -> documents.update(null,
                    new LambdaUpdateWrapper<Document>()
                            .eq(Document::getId, documentId)
                            .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                            .eq(Document::getUserId, userId)
                            .eq(Document::getProcessingStatus, DocumentProcessingStatus.PROCESSING)
                            .set(Document::getProcessingStatus, DocumentProcessingStatus.FAILED)
                            .set(Document::getProcessingError, error)
                            .set(Document::getProcessedAt, null)));
        } catch (RuntimeException statusFailure) {
            log.error("Failed to mark document {} processing as failed", documentId, statusFailure);
        }
    }

    private String publicError(RuntimeException exception) {
        String message = exception instanceof BusinessException ? exception.getMessage() : "文档处理失败";
        if (message == null || message.isBlank()) message = "文档处理失败";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
