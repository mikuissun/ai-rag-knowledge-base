package com.mikuissun.knowledgebase.document.embedding;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {
    private String apiKey = "";
    @NotBlank
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    @NotBlank
    private String model = "text-embedding-v4";
    @Min(1)
    @Max(4096)
    private int dimension = 1024;
    @Min(1)
    @Max(10)
    private int batchSize = 10;
    @Min(1)
    @Max(60)
    private int connectTimeoutSeconds = 10;
    @Min(1)
    @Max(300)
    private int readTimeoutSeconds = 60;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }
}
