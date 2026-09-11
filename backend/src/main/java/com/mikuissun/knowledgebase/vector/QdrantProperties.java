package com.mikuissun.knowledgebase.vector;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.qdrant")
public class QdrantProperties {
    @NotBlank
    private String host = "localhost";
    @Min(1)
    @Max(65535)
    private int grpcPort = 6334;
    @NotBlank
    private String collection = "knowledge_chunks";
    @Min(1)
    @Max(4096)
    private int dimension = 1024;
    @Min(1)
    @Max(1000)
    private int batchSize = 100;
    @Min(1)
    @Max(120)
    private int timeoutSeconds = 15;
    @Min(1)
    @Max(20)
    private int searchTopK = 5;
    @Min(1)
    @Max(20)
    private int maxSearchTopK = 20;

    @AssertTrue(message = "app.qdrant.search-top-k 不能大于 max-search-top-k")
    public boolean isSearchLimitValid() {
        return searchTopK <= maxSearchTopK;
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getGrpcPort() { return grpcPort; }
    public void setGrpcPort(int grpcPort) { this.grpcPort = grpcPort; }
    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getSearchTopK() { return searchTopK; }
    public void setSearchTopK(int searchTopK) { this.searchTopK = searchTopK; }
    public int getMaxSearchTopK() { return maxSearchTopK; }
    public void setMaxSearchTopK(int maxSearchTopK) { this.maxSearchTopK = maxSearchTopK; }
}
