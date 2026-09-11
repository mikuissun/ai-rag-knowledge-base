package com.mikuissun.knowledgebase.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
public class DashScopeChatModelService implements ChatModelService {
    private static final Logger log = LoggerFactory.getLogger(DashScopeChatModelService.class);
    private final ChatModelProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final URI endpoint;

    @Autowired
    public DashScopeChatModelService(ChatModelProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds())).build());
    }

    DashScopeChatModelService(ChatModelProperties properties, ObjectMapper objectMapper, HttpClient client) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = client;
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        try {
            endpoint = URI.create(base + "/chat/completions");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid app.llm.base-url", exception);
        }
        if (!Set.of("https", "http").contains(endpoint.getScheme())) {
            throw new IllegalStateException("app.llm.base-url must use HTTP or HTTPS");
        }
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        validate(systemPrompt, userPrompt);
        try {
            HttpResponse<byte[]> response = client.send(request(systemPrompt, userPrompt, false),
                    HttpResponse.BodyHandlers.ofByteArray());
            requireSuccess(response.statusCode(), response.headers().firstValue("x-request-id").orElse("unknown"));
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) throw new IOException("Empty chat completion");
            return content;
        } catch (BusinessException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new BusinessException(504, "LLM 服务调用超时");
        } catch (ConnectException exception) {
            throw new BusinessException(503, "LLM 服务暂时不可用");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "LLM 服务调用被中断");
        } catch (IOException | RuntimeException exception) {
            log.warn("DashScope chat request could not be completed", exception);
            throw new BusinessException(502, "LLM 服务调用失败");
        }
    }

    @Override
    public void streamChat(String systemPrompt, String userPrompt, Consumer<String> deltaConsumer) {
        validate(systemPrompt, userPrompt);
        if (deltaConsumer == null) throw new IllegalArgumentException("deltaConsumer must not be null");
        try {
            HttpResponse<Stream<String>> response = client.send(request(systemPrompt, userPrompt, true),
                    HttpResponse.BodyHandlers.ofLines());
            requireSuccess(response.statusCode(), response.headers().firstValue("x-request-id").orElse("unknown"));
            boolean[] emitted = {false};
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> consumeEvent(line, deltaConsumer, emitted));
            }
            if (!emitted[0]) throw new IOException("Empty streaming completion");
        } catch (BusinessException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new BusinessException(504, "LLM 服务调用超时");
        } catch (ConnectException exception) {
            throw new BusinessException(503, "LLM 服务暂时不可用");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "LLM 服务调用被中断");
        } catch (StreamParsingException | UncheckedIOException exception) {
            log.warn("DashScope streaming response could not be parsed", exception);
            throw new BusinessException(502, "LLM 流式服务调用失败");
        } catch (IOException exception) {
            log.warn("DashScope streaming chat request could not be completed", exception);
            throw new BusinessException(502, "LLM 流式服务调用失败");
        }
    }

    private HttpRequest request(String systemPrompt, String userPrompt, boolean stream) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(new ChatRequest(properties.getModel(), List.of(
                new Message("system", systemPrompt), new Message("user", userPrompt)),
                stream, properties.getTemperature(), properties.getMaxTokens()));
        return HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    private void consumeEvent(String line, Consumer<String> deltaConsumer, boolean[] emitted) {
        if (line == null || !line.startsWith("data:")) return;
        String data = line.substring(5).trim();
        if (data.isBlank() || "[DONE]".equals(data)) return;
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            if (content.isTextual() && !content.asText().isEmpty()) {
                deltaConsumer.accept(content.asText());
                emitted[0] = true;
            }
        } catch (IOException exception) {
            throw new StreamParsingException(exception);
        }
    }

    private void validate(String systemPrompt, String userPrompt) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(503, "未配置 DASHSCOPE_API_KEY，无法调用 LLM");
        }
        if (systemPrompt == null || systemPrompt.isBlank() || userPrompt == null || userPrompt.isBlank()) {
            throw new BusinessException(400, "LLM Prompt 不能为空");
        }
    }

    private void requireSuccess(int statusCode, String requestId) {
        if (statusCode < 200 || statusCode >= 300) {
            log.warn("DashScope chat request failed: HTTP {}, request-id={}", statusCode, requestId);
            throw new BusinessException(statusCode == 429 || statusCode >= 500 ? 503 : 502,
                    "LLM 服务调用失败");
        }
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    private record ChatRequest(String model, List<Message> messages, boolean stream,
                               double temperature, int max_tokens) { }
    private record Message(String role, String content) { }

    private static final class StreamParsingException extends RuntimeException {
        private StreamParsingException(Throwable cause) { super(cause); }
    }
}
