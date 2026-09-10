package com.mikuissun.knowledgebase.document.dto;

import com.mikuissun.knowledgebase.document.entity.Document;
import java.time.LocalDateTime;

public record DocumentListResponse(Long id, Long knowledgeBaseId, String originalName,
        String fileType, Long fileSize, Integer status, LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static DocumentListResponse from(Document document) {
        return new DocumentListResponse(document.getId(), document.getKnowledgeBaseId(), document.getOriginalName(),
                document.getFileType(), document.getFileSize(), document.getStatus(),
                document.getCreatedAt(), document.getUpdatedAt());
    }
}
