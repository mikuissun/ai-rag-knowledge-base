package com.mikuissun.knowledgebase.rag.prompt;

public record RagPromptSource(String documentName, Long documentId, Integer chunkIndex, String content) {
}
