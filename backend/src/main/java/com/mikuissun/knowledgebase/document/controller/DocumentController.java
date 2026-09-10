package com.mikuissun.knowledgebase.document.controller;

import com.mikuissun.knowledgebase.auth.CurrentUserContext;
import com.mikuissun.knowledgebase.common.api.ApiResponse;
import com.mikuissun.knowledgebase.document.dto.DocumentResponse;
import com.mikuissun.knowledgebase.document.dto.DocumentListResponse;
import com.mikuissun.knowledgebase.document.service.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
public class DocumentController {
    private final DocumentService service;
    public DocumentController(DocumentService service) { this.service = service; }

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
}
