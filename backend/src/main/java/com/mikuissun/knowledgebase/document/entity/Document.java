package com.mikuissun.knowledgebase.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("documents")
public class Document {
    private Long id;
    private Long knowledgeBaseId;
    private Long userId;
    private String originalName;
    private String storedName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private String contentText;
    private Integer status;
    private String processingStatus;
    private String processingError;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }

    public String getProcessingError() { return processingError; }
    public void setProcessingError(String processingError) { this.processingError = processingError; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
