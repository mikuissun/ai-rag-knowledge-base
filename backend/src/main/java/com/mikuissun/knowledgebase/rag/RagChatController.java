package com.mikuissun.knowledgebase.rag;

import com.mikuissun.knowledgebase.auth.CurrentUserContext;
import com.mikuissun.knowledgebase.common.api.ApiResponse;
import com.mikuissun.knowledgebase.rag.dto.RagChatRequest;
import com.mikuissun.knowledgebase.rag.dto.RagChatResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/chat")
public class RagChatController {
    private final RagChatService service;

    public RagChatController(RagChatService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<RagChatResponse> chat(@PathVariable Long knowledgeBaseId,
                                             @Valid @RequestBody RagChatRequest request) {
        Long userId = CurrentUserContext.requireCurrentUser().id();
        return ApiResponse.ok(service.chat(knowledgeBaseId, userId, request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long knowledgeBaseId,
                             @Valid @RequestBody RagChatRequest request,
                             HttpServletResponse response) {
        Long userId = CurrentUserContext.requireCurrentUser().id();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
        return service.stream(knowledgeBaseId, userId, request);
    }
}
