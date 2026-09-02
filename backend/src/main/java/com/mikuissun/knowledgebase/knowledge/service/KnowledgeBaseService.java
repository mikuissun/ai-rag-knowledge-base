package com.mikuissun.knowledgebase.knowledge.service;

import com.mikuissun.knowledgebase.knowledge.dto.CreateKnowledgeBaseRequest;
import com.mikuissun.knowledgebase.knowledge.dto.KnowledgeBaseResponse;
import com.mikuissun.knowledgebase.knowledge.dto.UpdateKnowledgeBaseRequest;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseResponse create(Long userId, CreateKnowledgeBaseRequest request);

    List<KnowledgeBaseResponse> listByUserId(Long userId);

    KnowledgeBaseResponse getByIdAndUserId(Long id, Long userId);

    KnowledgeBaseResponse update(Long id, Long userId, UpdateKnowledgeBaseRequest request);

    void delete(Long id, Long userId);
}
