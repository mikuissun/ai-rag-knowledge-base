package com.mikuissun.knowledgebase.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashScopeChatModelServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<JsonNode> requests = new ArrayList<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            JsonNode request = json.readTree(exchange.getRequestBody());
            requests.add(request);
            byte[] response;
            if (request.path("stream").asBoolean()) {
                response = ("data: {\"choices\":[{\"delta\":{\"content\":\"流式\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"回答\"}}]}\n\n"
                        + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            } else {
                response = "{\"choices\":[{\"message\":{\"content\":\"普通回答\"}}]}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
            }
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void callsNonStreamingChatCompletion() {
        DashScopeChatModelService service = service("local-test-key");
        assertThat(service.chat("系统规则", "用户问题")).isEqualTo("普通回答");
        assertThat(service.modelName()).isEqualTo("qwen-plus");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.path("model").asText()).isEqualTo("qwen-plus");
            assertThat(request.path("stream").asBoolean()).isFalse();
            assertThat(request.path("messages").path(0).path("role").asText()).isEqualTo("system");
            assertThat(request.path("messages").path(1).path("content").asText()).isEqualTo("用户问题");
        });
    }

    @Test
    void parsesStreamingChatCompletionDeltas() {
        DashScopeChatModelService service = service("local-test-key");
        List<String> deltas = new ArrayList<>();
        service.streamChat("系统规则", "用户问题", deltas::add);
        assertThat(deltas).containsExactly("流式", "回答");
        assertThat(requests).singleElement()
                .satisfies(request -> assertThat(request.path("stream").asBoolean()).isTrue());
    }

    @Test
    void missingApiKeyIsRejectedWithoutNetworkCall() {
        assertThatThrownBy(() -> service("").chat("系统", "问题"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(503);
        assertThat(calls).hasValue(0);
    }

    @Test
    void providerFailureIsMappedWithoutLeakingResponseBody() {
        server.removeContext("/v1/chat/completions");
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = "real-provider-detail-must-not-leak".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        assertThatThrownBy(() -> service("local-test-key").chat("系统", "问题"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("LLM 服务调用失败")
                .extracting("code").isEqualTo(503);
    }

    private DashScopeChatModelService service(String key) {
        ChatModelProperties properties = new ChatModelProperties();
        properties.setApiKey(key);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        properties.setModel("qwen-plus");
        properties.setReadTimeoutSeconds(5);
        return new DashScopeChatModelService(properties, json, HttpClient.newHttpClient());
    }
}
