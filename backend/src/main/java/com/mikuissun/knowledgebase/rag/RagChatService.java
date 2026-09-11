package com.mikuissun.knowledgebase.rag;

import com.mikuissun.knowledgebase.rag.dto.RagChatRequest;
import com.mikuissun.knowledgebase.rag.dto.RagChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RagChatService {
    RagChatResponse chat(Long knowledgeBaseId, Long userId, RagChatRequest request);
    SseEmitter stream(Long knowledgeBaseId, Long userId, RagChatRequest request);
}
