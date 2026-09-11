package com.mikuissun.knowledgebase.rag.dto;

import java.util.List;

public record RagChatResponse(String answer, List<CitationResponse> sources) {
}
