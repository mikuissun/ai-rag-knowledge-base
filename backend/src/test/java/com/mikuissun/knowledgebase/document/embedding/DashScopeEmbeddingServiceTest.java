package com.mikuissun.knowledgebase.document.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class DashScopeEmbeddingServiceTest {
    private HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            calls.incrementAndGet();
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            var request = json.readTree(exchange.getRequestBody());
            int size = request.path("input").size();
            List<Map<String, Object>> data = new ArrayList<>();
            for (int index = size - 1; index >= 0; index--) {
                data.add(Map.of("index", index, "embedding",
                        List.of((float) index, (float) size, 0.5f)));
            }
            byte[] response = json.writeValueAsBytes(Map.of("data", data, "model", "test-model"));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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
    void batchesRequestsAndRestoresResponseOrder() {
        DashScopeEmbeddingService service = service("local-test-key");
        List<float[]> vectors = service.embedBatch(List.of("甲", "乙", "丙"));
        assertThat(calls).hasValue(2);
        assertThat(authorizations).containsOnly("Bearer local-test-key");
        assertThat(vectors).hasSize(3);
        assertThat(vectors.get(0)).containsExactly(0f, 2f, 0.5f);
        assertThat(vectors.get(1)).containsExactly(1f, 2f, 0.5f);
        assertThat(vectors.get(2)).containsExactly(0f, 1f, 0.5f);
        assertThat(service.modelName()).isEqualTo("text-embedding-v4");
        assertThat(service.dimension()).isEqualTo(3);
    }

    @Test
    void missingApiKeyIsRejectedWithoutNetworkCall() {
        assertThatThrownBy(() -> service("").embedBatch(List.of("文本")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(503);
        assertThat(calls).hasValue(0);
    }

    @Test
    void malformedEmbeddingResponseIsRejected() {
        server.removeContext("/v1/embeddings");
        server.createContext("/v1/embeddings", exchange -> {
            byte[] response = "{\"data\":[{\"index\":0,\"embedding\":[1]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        assertThatThrownBy(() -> service("local-test-key").embedBatch(List.of("文本")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(502);
    }

    private DashScopeEmbeddingService service(String key) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setApiKey(key);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        properties.setModel("text-embedding-v4");
        properties.setDimension(3);
        properties.setBatchSize(2);
        properties.setReadTimeoutSeconds(5);
        return new DashScopeEmbeddingService(properties, json, HttpClient.newHttpClient());
    }
}
