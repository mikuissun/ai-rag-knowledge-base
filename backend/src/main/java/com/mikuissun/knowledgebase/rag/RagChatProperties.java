package com.mikuissun.knowledgebase.rag;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.rag.chat")
public class RagChatProperties {
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private float minScore = 0.5f;
    @Min(100)
    @Max(200000)
    private int maxContextChars = 12000;
    @Min(5)
    @Max(600)
    private int sseTimeoutSeconds = 120;

    public float getMinScore() { return minScore; }
    public void setMinScore(float minScore) { this.minScore = minScore; }
    public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(int maxContextChars) { this.maxContextChars = maxContextChars; }
    public int getSseTimeoutSeconds() { return sseTimeoutSeconds; }
    public void setSseTimeoutSeconds(int sseTimeoutSeconds) { this.sseTimeoutSeconds = sseTimeoutSeconds; }
}
