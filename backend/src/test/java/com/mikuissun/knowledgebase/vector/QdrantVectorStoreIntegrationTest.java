package com.mikuissun.knowledgebase.vector;

import com.mikuissun.knowledgebase.common.exception.BusinessException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "QDRANT_INTEGRATION_TEST", matches = "true")
class QdrantVectorStoreIntegrationTest {
    QdrantClient client;
    QdrantProperties properties;
    QdrantVectorStoreService store;

    @BeforeEach
    void setup() {
        String host = System.getenv().getOrDefault("QDRANT_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("QDRANT_GRPC_PORT", "6334"));
        client = new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());
        properties = new QdrantProperties();
        properties.setHost(host);
        properties.setGrpcPort(port);
        properties.setCollection("stage6_test_" + UUID.randomUUID().toString().replace("-", ""));
        properties.setDimension(4);
        properties.setBatchSize(2);
        properties.setTimeoutSeconds(10);
        store = new QdrantVectorStoreService(client, properties);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (client.collectionExistsAsync(properties.getCollection()).get()) {
                client.deleteCollectionAsync(properties.getCollection()).get();
            }
        } finally {
            client.close();
        }
    }

    @Test
    void createsCompatibleCollectionAndSupportsReplaceSearchAndDelete() throws Exception {
        store.ensureCollection();
        Collections.VectorParams params = client.getCollectionInfoAsync(properties.getCollection()).get()
                .getConfig().getParams().getVectorsConfig().getParams();
        assertThat(params.getSize()).isEqualTo(4);
        assertThat(params.getDistance()).isEqualTo(Collections.Distance.Cosine);

        List<VectorPoint> first = List.of(
                point(1L, 11L, 101L, 1001L, 0, "第一段", 1f, 0f, 0f, 0f),
                point(2L, 11L, 101L, 1001L, 1, "第二段", 0.9f, 0.1f, 0f, 0f));
        store.replaceDocumentPoints(1001L, 101L, 11L, first);
        store.replaceDocumentPoints(1001L, 101L, 11L, first);
        store.replaceDocumentPoints(2002L, 101L, 22L,
                List.of(point(3L, 22L, 101L, 2002L, 0, "其他用户", 1f, 0f, 0f, 0f)));
        store.replaceDocumentPoints(1001L, 202L, 33L,
                List.of(point(4L, 33L, 202L, 1001L, 0, "其他知识库", 1f, 0f, 0f, 0f)));

        List<VectorSearchResult> results = store.similaritySearch(
                1001L, 101L, new float[]{1f, 0f, 0f, 0f}, 10);
        assertThat(results).hasSize(2)
                .extracting(VectorSearchResult::content).containsExactly("第一段", "第二段");
        assertThat(results).allSatisfy(result -> assertThat(result.score()).isPositive());

        store.replaceDocumentPoints(1001L, 101L, 11L,
                List.of(point(5L, 11L, 101L, 1001L, 0, "替换后的段落", 1f, 0f, 0f, 0f)));
        assertThat(store.similaritySearch(1001L, 101L, new float[]{1f, 0f, 0f, 0f}, 10))
                .singleElement().extracting(VectorSearchResult::content).isEqualTo("替换后的段落");

        store.deleteDocumentPoints(1001L, 101L, 11L);
        assertThat(store.similaritySearch(1001L, 101L, new float[]{1f, 0f, 0f, 0f}, 10)).isEmpty();
    }

    @Test
    void rejectsIncompatibleExistingCollection() throws Exception {
        client.createCollectionAsync(properties.getCollection(), Collections.VectorParams.newBuilder()
                .setSize(3).setDistance(Collections.Distance.Dot).build()).get();
        assertThatThrownBy(store::ensureCollection)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不兼容");
    }

    private VectorPoint point(long chunkId, long documentId, long knowledgeBaseId,
                              long userId, int index, String content, float... vector) {
        return new VectorPoint(chunkId, documentId, knowledgeBaseId, userId, index, content, vector);
    }
}
