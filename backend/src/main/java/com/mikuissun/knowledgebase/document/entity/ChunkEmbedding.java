package com.mikuissun.knowledgebase.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("chunk_embeddings")
public class ChunkEmbedding {
    private Long id;
    private Long chunkId;
    private String modelName;
    private Integer dimension;
    private String embedding;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Integer getDimension() { return dimension; }
    public void setDimension(Integer dimension) { this.dimension = dimension; }
    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
