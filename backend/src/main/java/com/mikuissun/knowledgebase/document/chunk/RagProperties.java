package com.mikuissun.knowledgebase.document.chunk;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    @Min(100)
    @Max(10000)
    private int chunkSize = 1000;

    @Min(0)
    private int chunkOverlap = 150;

    @Min(1)
    @Max(10000)
    private int maxChunksPerDocument = 500;

    @AssertTrue(message = "app.rag.chunk-overlap must be smaller than app.rag.chunk-size")
    public boolean isOverlapValid() {
        return chunkOverlap < chunkSize;
    }

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public int getMaxChunksPerDocument() { return maxChunksPerDocument; }
    public void setMaxChunksPerDocument(int maxChunksPerDocument) {
        this.maxChunksPerDocument = maxChunksPerDocument;
    }
}
