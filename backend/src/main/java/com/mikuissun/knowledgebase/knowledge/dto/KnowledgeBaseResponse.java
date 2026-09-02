package com.mikuissun.knowledgebase.knowledge.dto;

import com.mikuissun.knowledgebase.knowledge.entity.KnowledgeBase;

import java.time.LocalDateTime;

public record KnowledgeBaseResponse(Long id, String name, String description, Integer status,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(knowledgeBase.getId(), knowledgeBase.getName(), knowledgeBase.getDescription(),
                knowledgeBase.getStatus(), knowledgeBase.getCreatedAt(), knowledgeBase.getUpdatedAt());
    }
}
