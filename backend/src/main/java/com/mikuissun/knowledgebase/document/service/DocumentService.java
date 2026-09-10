package com.mikuissun.knowledgebase.document.service;

import com.mikuissun.knowledgebase.document.dto.DocumentResponse;
import com.mikuissun.knowledgebase.document.dto.DocumentListResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface DocumentService {
    DocumentResponse upload(Long knowledgeBaseId, Long userId, MultipartFile file);
    List<DocumentListResponse> list(Long knowledgeBaseId, Long userId);
    DocumentResponse get(Long knowledgeBaseId, Long documentId, Long userId);
    void delete(Long knowledgeBaseId, Long documentId, Long userId);
}
