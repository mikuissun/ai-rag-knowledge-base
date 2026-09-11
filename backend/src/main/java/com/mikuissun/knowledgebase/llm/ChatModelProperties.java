package com.mikuissun.knowledgebase.llm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.llm")
public class ChatModelProperties {
    @NotBlank
    @Pattern(regexp = "(?i)qwen", message = "app.llm.provider 当前仅支持 qwen")
    private String provider = "qwen";
    private String apiKey = "";
    @NotBlank
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    @NotBlank
    private String model = "qwen-plus";
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature = 0.2;
    @Min(1)
    @Max(8192)
    private int maxTokens = 1500;
    @Min(1)
    @Max(60)
    private int connectTimeoutSeconds = 10;
    @Min(1)
    @Max(300)
    private int readTimeoutSeconds = 120;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
}
