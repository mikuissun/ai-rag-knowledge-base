package com.mikuissun.knowledgebase.document.controller;

import com.mikuissun.knowledgebase.auth.CurrentUserContext;
import com.mikuissun.knowledgebase.common.api.ApiResponse;
import com.mikuissun.knowledgebase.document.dto.DocumentResponse;
import com.mikuissun.knowledgebase.document.dto.DocumentListResponse;
import com.mikuissun.knowledgebase.document.service.DocumentService;
import com.mikuissun.knowledgebase.document.processing.DocumentProcessingService;
import com.mikuissun.knowledgebase.document.processing.dto.DocumentProcessResponse;
import com.mikuissun.knowledgebase.document.indexing.DocumentIndexingService;
import com.mikuissun.knowledgebase.document.indexing.dto.DocumentIndexResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
public class DocumentController {
    private final DocumentService service;
    private final DocumentProcessingService processingService;
    private final DocumentIndexingService indexingService;
    public DocumentController(DocumentService service, DocumentProcessingService processingService,
                              DocumentIndexingService indexingService) {
        this.service = service;
        this.processingService = processingService;
        this.indexingService = indexingService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<DocumentResponse> upload(@PathVariable Long knowledgeBaseId,
                                                @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.upload(knowledgeBaseId, CurrentUserContext.requireCurrentUser().id(), file));
    }
    @GetMapping
    public ApiResponse<List<DocumentListResponse>> list(@PathVariable Long knowledgeBaseId) {
        return ApiResponse.ok(service.list(knowledgeBaseId, CurrentUserContext.requireCurrentUser().id()));
    }
    @GetMapping("/{documentId}")
    public ApiResponse<DocumentResponse> get(@PathVariable Long knowledgeBaseId, @PathVariable Long documentId) {
        return ApiResponse.ok(service.get(knowledgeBaseId, documentId, CurrentUserContext.requireCurrentUser().id()));
    }
    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(@PathVariable Long knowledgeBaseId, @PathVariable Long documentId) {
        service.delete(knowledgeBaseId, documentId, CurrentUserContext.requireCurrentUser().id());
        return ApiResponse.ok();
    }

    @PostMapping("/{documentId}/process")
    public ApiResponse<DocumentProcessResponse> process(@PathVariable Long knowledgeBaseId,
                                                        @PathVariable Long documentId) {
        return ApiResponse.ok(processingService.process(
                knowledgeBaseId, documentId, CurrentUserContext.requireCurrentUser().id()));
    }

    @PostMapping("/{documentId}/index")
    public ApiResponse<DocumentIndexResponse> index(@PathVariable Long knowledgeBaseId,
                                                    @PathVariable Long documentId) {
        return ApiResponse.ok(indexingService.index(
                knowledgeBaseId, documentId, CurrentUserContext.requireCurrentUser().id()));
    }
}
