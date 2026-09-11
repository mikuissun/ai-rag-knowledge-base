package com.mikuissun.knowledgebase.search;

import com.mikuissun.knowledgebase.auth.CurrentUserContext;
import com.mikuissun.knowledgebase.common.api.ApiResponse;
import com.mikuissun.knowledgebase.search.dto.KnowledgeSearchRequest;
import com.mikuissun.knowledgebase.search.dto.KnowledgeSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/search")
public class KnowledgeSearchController {
    private final KnowledgeSearchService service;

    public KnowledgeSearchController(KnowledgeSearchService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<KnowledgeSearchResponse> search(@PathVariable Long knowledgeBaseId,
                                                       @Valid @RequestBody KnowledgeSearchRequest request) {
        return ApiResponse.ok(service.search(
                knowledgeBaseId, CurrentUserContext.requireCurrentUser().id(), request));
    }
}
