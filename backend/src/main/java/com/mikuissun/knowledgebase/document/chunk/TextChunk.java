package com.mikuissun.knowledgebase.document.chunk;

public record TextChunk(int index, String content, int charCount, int tokenEstimate) {
}
