package com.mikuissun.knowledgebase.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("document_chunks")
public class DocumentChunk {
    private Long id;
    private Long userId;
    private Long knowledgeBaseId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer charCount;
    private Integer tokenEstimate;
    private String embeddingStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }
    public Integer getTokenEstimate() { return tokenEstimate; }
    public void setTokenEstimate(Integer tokenEstimate) { this.tokenEstimate = tokenEstimate; }
    public String getEmbeddingStatus() { return embeddingStatus; }
    public void setEmbeddingStatus(String embeddingStatus) { this.embeddingStatus = embeddingStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
