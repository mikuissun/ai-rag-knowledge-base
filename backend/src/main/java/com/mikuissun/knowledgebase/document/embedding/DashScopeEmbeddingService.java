package com.mikuissun.knowledgebase.document.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class DashScopeEmbeddingService implements EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingService.class);
    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final URI endpoint;

    @org.springframework.beans.factory.annotation.Autowired
    public DashScopeEmbeddingService(EmbeddingProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds())).build());
    }

    DashScopeEmbeddingService(EmbeddingProperties properties, ObjectMapper objectMapper, HttpClient client) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = client;
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        try {
            endpoint = URI.create(base + "/embeddings");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid app.embedding.base-url", exception);
        }
        if (!Set.of("https", "http").contains(endpoint.getScheme())) {
            throw new IllegalStateException("app.embedding.base-url must use HTTP or HTTPS");
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(503, "未配置 DASHSCOPE_API_KEY，无法生成 Embedding");
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new BusinessException(400, "Embedding 文本不能为空");
        }

        List<float[]> result = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += properties.getBatchSize()) {
            int end = Math.min(start + properties.getBatchSize(), texts.size());
            result.addAll(call(texts.subList(start, end)));
        }
        return List.copyOf(result);
    }

    private List<float[]> call(List<String> texts) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(
                    new EmbeddingRequest(properties.getModel(), texts, properties.getDimension()));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("DashScope embedding request failed: HTTP {}, request-id={}",
                        response.statusCode(), response.headers().firstValue("x-request-id").orElse("unknown"));
                throw new BusinessException(502, "Embedding 服务调用失败");
            }
            EmbeddingResponse parsed = objectMapper.readValue(response.body(), EmbeddingResponse.class);
            if (parsed.data() == null || parsed.data().size() != texts.size()) {
                throw new IOException("Unexpected embedding count");
            }
            EmbeddingItem[] ordered = new EmbeddingItem[texts.size()];
            for (EmbeddingItem item : parsed.data()) {
                if (item == null || item.index() < 0 || item.index() >= ordered.length
                        || ordered[item.index()] != null || item.embedding() == null
                        || item.embedding().length != properties.getDimension()) {
                    throw new IOException("Invalid embedding response");
                }
                for (float value : item.embedding()) {
                    if (!Float.isFinite(value)) throw new IOException("Non-finite embedding value");
                }
                ordered[item.index()] = item;
            }
            List<float[]> vectors = new ArrayList<>(ordered.length);
            for (EmbeddingItem item : ordered) {
                if (item == null) throw new IOException("Missing embedding index");
                vectors.add(item.embedding());
            }
            return vectors;
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "Embedding 服务调用被中断");
        } catch (IOException | RuntimeException exception) {
            log.warn("DashScope embedding request could not be completed", exception);
            throw new BusinessException(502, "Embedding 服务调用失败");
        }
    }

    @Override public String modelName() { return properties.getModel(); }
    @Override public int dimension() { return properties.getDimension(); }

    private record EmbeddingRequest(String model, List<String> input, int dimensions) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<EmbeddingItem> data) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingItem(int index, float[] embedding) { }
}
