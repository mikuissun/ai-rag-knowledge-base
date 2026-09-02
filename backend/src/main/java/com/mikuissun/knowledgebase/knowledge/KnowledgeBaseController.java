package com.mikuissun.knowledgebase.knowledge;

import com.mikuissun.knowledgebase.auth.CurrentUserContext;
import com.mikuissun.knowledgebase.common.api.ApiResponse;
import com.mikuissun.knowledgebase.knowledge.dto.CreateKnowledgeBaseRequest;
import com.mikuissun.knowledgebase.knowledge.dto.KnowledgeBaseResponse;
import com.mikuissun.knowledgebase.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.mikuissun.knowledgebase.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.ok(knowledgeBaseService.create(CurrentUserContext.requireCurrentUser().id(), request));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> list() {
        return ApiResponse.ok(knowledgeBaseService.listByUserId(CurrentUserContext.requireCurrentUser().id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeBaseService.getByIdAndUserId(id, CurrentUserContext.requireCurrentUser().id()));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        return ApiResponse.ok(knowledgeBaseService.update(id, CurrentUserContext.requireCurrentUser().id(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id, CurrentUserContext.requireCurrentUser().id());
        return ApiResponse.ok();
    }
}
