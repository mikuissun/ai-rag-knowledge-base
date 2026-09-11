package com.mikuissun.knowledgebase.stage6;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.auth.JwtTokenService;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.document.embedding.EmbeddingService;
import com.mikuissun.knowledgebase.document.entity.ChunkEmbedding;
import com.mikuissun.knowledgebase.document.entity.Document;
import com.mikuissun.knowledgebase.document.entity.DocumentChunk;
import com.mikuissun.knowledgebase.document.indexing.DocumentIndexingStatus;
import com.mikuissun.knowledgebase.document.mapper.ChunkEmbeddingMapper;
import com.mikuissun.knowledgebase.document.mapper.DocumentChunkMapper;
import com.mikuissun.knowledgebase.document.mapper.DocumentMapper;
import com.mikuissun.knowledgebase.document.processing.DocumentProcessingStatus;
import com.mikuissun.knowledgebase.knowledge.entity.KnowledgeBase;
import com.mikuissun.knowledgebase.knowledge.mapper.KnowledgeBaseMapper;
import com.mikuissun.knowledgebase.user.entity.User;
import com.mikuissun.knowledgebase.user.mapper.UserMapper;
import com.mikuissun.knowledgebase.vector.VectorPoint;
import com.mikuissun.knowledgebase.vector.VectorSearchResult;
import com.mikuissun.knowledgebase.vector.VectorStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.qdrant.dimension=4", "app.embedding.dimension=4"})
@AutoConfigureMockMvc
@ContextConfiguration(classes = {
        com.mikuissun.knowledgebase.KnowledgeBaseApplication.class,
        Stage6IntegrationTest.Stage6TestConfig.class
})
class Stage6IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper users;
    @Autowired KnowledgeBaseMapper knowledgeBases;
    @Autowired DocumentMapper documents;
    @Autowired DocumentChunkMapper chunks;
    @Autowired ChunkEmbeddingMapper embeddings;
    @Autowired JwtTokenService tokens;
    @Autowired FakeVectorStore vectorStore;
    @Autowired FakeEmbeddingService embeddingService;

    User userA;
    User userB;
    String tokenA;
    String tokenB;
    long kbA;
    long secondKbA;
    long kbB;

    @BeforeEach
    void setup() {
        vectorStore.reset();
        embeddingService.reset();
        userA = user();
        userB = user();
        tokenA = tokens.generateToken(userA);
        tokenB = tokens.generateToken(userB);
        kbA = knowledgeBase(userA.getId());
        secondKbA = knowledgeBase(userA.getId());
        kbB = knowledgeBase(userB.getId());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM documents WHERE user_id IN (?, ?)", userA.getId(), userB.getId());
        jdbc.update("DELETE FROM knowledge_bases WHERE user_id IN (?, ?)", userA.getId(), userB.getId());
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", userA.getId(), userB.getId());
    }

    @Test
    void indexingIsIdempotentAndReplacesOldDocumentPoints() throws Exception {
        long documentId = processedDocument(userA.getId(), kbA, "第一版内容", "第二个文本块");

        mvc.perform(post(indexEndpoint(kbA, documentId)).header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId))
                .andExpect(jsonPath("$.data.pointCount").value(2))
                .andExpect(jsonPath("$.data.collection").value("test-knowledge-chunks"))
                .andExpect(jsonPath("$.data.indexingStatus").value("INDEXED"));
        assertThat(vectorStore.documentPoints(userA.getId(), kbA, documentId)).hasSize(2);

        mvc.perform(post(indexEndpoint(kbA, documentId)).header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pointCount").value(2));
        assertThat(vectorStore.documentPoints(userA.getId(), kbA, documentId)).hasSize(2);

        jdbc.update("DELETE FROM document_chunks WHERE document_id=?", documentId);
        addChunk(userA.getId(), kbA, documentId, 0, "重新处理后的唯一文本块");
        mvc.perform(post(indexEndpoint(kbA, documentId)).header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pointCount").value(1));
        assertThat(vectorStore.documentPoints(userA.getId(), kbA, documentId))
                .singleElement().extracting(VectorPoint::content).isEqualTo("重新处理后的唯一文本块");
        assertThat(jdbc.queryForObject("SELECT indexing_status FROM documents WHERE id=?",
                String.class, documentId)).isEqualTo("INDEXED");
        assertThat(jdbc.queryForObject("SELECT indexed_at IS NOT NULL FROM documents WHERE id=?",
                Boolean.class, documentId)).isTrue();
    }

    @Test
    void indexingEnforcesUserAndKnowledgeBaseOwnership() throws Exception {
        long documentId = processedDocument(userA.getId(), kbA, "用户 A 的内容");

        mvc.perform(post(indexEndpoint(kbA, documentId)).header("Authorization", bearer(tokenB)))
                .andExpect(status().isNotFound());
        mvc.perform(post(indexEndpoint(kbB, documentId)).header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
        mvc.perform(post(indexEndpoint(secondKbA, documentId)).header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
        mvc.perform(post(indexEndpoint(kbA, documentId))).andExpect(status().isUnauthorized());
        assertThat(vectorStore.totalPoints()).isZero();
    }

    @Test
    void indexingFailureMarksDocumentFailed() throws Exception {
        long documentId = processedDocument(userA.getId(), kbA, "将触发 Qdrant 失败");
        vectorStore.failReplace.set(true);

        mvc.perform(post(indexEndpoint(kbA, documentId)).header("Authorization", bearer(tokenA)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
        assertThat(jdbc.queryForObject("SELECT indexing_status FROM documents WHERE id=?",
                String.class, documentId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT indexing_error FROM documents WHERE id=?",
                String.class, documentId)).isEqualTo("测试 Qdrant 不可用");
    }

    @Test
    void searchUsesTenantFiltersAndReturnsTopKPayload() throws Exception {
        long documentA = processedDocument(userA.getId(), kbA, "用户 A 可检索内容");
        long documentB = processedDocument(userB.getId(), kbB, "用户 B 私有内容");
        mvc.perform(post(indexEndpoint(kbA, documentA)).header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
        mvc.perform(post(indexEndpoint(kbB, documentB)).header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/knowledge-bases/{id}/search", kbA)
                        .header("Authorization", bearer(tokenA))
                        .contentType("application/json")
                        .content("{\"query\":\"  企业知识库检索  \",\"topK\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(kbA))
                .andExpect(jsonPath("$.data.query").value("企业知识库检索"))
                .andExpect(jsonPath("$.data.topK").value(1))
                .andExpect(jsonPath("$.data.results.length()").value(1))
                .andExpect(jsonPath("$.data.results[0].documentId").value(documentA))
                .andExpect(jsonPath("$.data.results[0].content").value("用户 A 可检索内容"))
                .andExpect(jsonPath("$.data.results[0].score").isNumber());
        assertThat(vectorStore.lastUserId).isEqualTo(userA.getId());
        assertThat(vectorStore.lastKnowledgeBaseId).isEqualTo(kbA);
        assertThat(embeddingService.lastTexts).containsExactly("企业知识库检索");

        mvc.perform(post("/api/knowledge-bases/{id}/search", kbA)
                        .header("Authorization", bearer(tokenB))
                        .contentType("application/json").content("{\"query\":\"越权检索\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/knowledge-bases/{id}/search", kbA)
                        .header("Authorization", bearer(tokenA))
                        .contentType("application/json").content("{\"query\":\"测试\",\"topK\":21}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unavailableSearchAndIndexedDocumentDeletionAreHandled() throws Exception {
        long documentId = processedDocument(userA.getId(), kbA, "待删除向量");
        mvc.perform(post(indexEndpoint(kbA, documentId)).header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
        assertThat(vectorStore.documentPoints(userA.getId(), kbA, documentId)).hasSize(1);

        vectorStore.failSearch.set(true);
        mvc.perform(post("/api/knowledge-bases/{id}/search", kbA)
                        .header("Authorization", bearer(tokenA))
                        .contentType("application/json").content("{\"query\":\"失败测试\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503));

        mvc.perform(delete("/api/knowledge-bases/{kb}/documents/{doc}", kbA, documentId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
        assertThat(vectorStore.documentPoints(userA.getId(), kbA, documentId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE id=?",
                Integer.class, documentId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_chunks WHERE document_id=?",
                Integer.class, documentId)).isZero();
    }

    private User user() {
        User user = new User();
        user.setUsername("stage6" + UUID.randomUUID().toString().replace("-", ""));
        user.setPassword("unused-test-only-hash");
        user.setStatus(1);
        users.insert(user);
        return user;
    }

    private long knowledgeBase(long userId) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setUserId(userId);
        knowledgeBase.setName("Stage 6 test");
        knowledgeBase.setStatus(1);
        knowledgeBases.insert(knowledgeBase);
        return knowledgeBase.getId();
    }

    private long processedDocument(long userId, long knowledgeBaseId, String... contents) throws Exception {
        Document document = new Document();
        document.setUserId(userId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setOriginalName("stage6.txt");
        document.setStoredName(UUID.randomUUID() + ".txt");
        document.setFilePath(userId + "/" + knowledgeBaseId + "/missing-stage6-file.txt");
        document.setFileType("txt");
        document.setFileSize(10L);
        document.setContentText(String.join("\n", contents));
        document.setStatus(1);
        document.setProcessingStatus(DocumentProcessingStatus.PROCESSED);
        document.setIndexingStatus(DocumentIndexingStatus.PENDING);
        documents.insert(document);
        for (int index = 0; index < contents.length; index++) {
            addChunk(userId, knowledgeBaseId, document.getId(), index, contents[index]);
        }
        return document.getId();
    }

    private long addChunk(long userId, long knowledgeBaseId, long documentId,
                          int chunkIndex, String content) throws Exception {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setUserId(userId);
        chunk.setKnowledgeBaseId(knowledgeBaseId);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunk.setTokenEstimate(content.length());
        chunk.setEmbeddingStatus("COMPLETED");
        chunks.insert(chunk);
        ChunkEmbedding embedding = new ChunkEmbedding();
        embedding.setChunkId(chunk.getId());
        embedding.setModelName("fake-embedding");
        embedding.setDimension(4);
        embedding.setEmbedding(json.writeValueAsString(new float[]{content.length(), 0.5f, 0.25f, 1f}));
        embeddings.insert(embedding);
        return chunk.getId();
    }

    private String indexEndpoint(long knowledgeBaseId, long documentId) {
        return "/api/knowledge-bases/" + knowledgeBaseId + "/documents/" + documentId + "/index";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Stage6TestConfig {
        @Bean
        @Primary
        FakeVectorStore fakeVectorStore() {
            return new FakeVectorStore();
        }

        @Bean
        @Primary
        FakeEmbeddingService fakeEmbeddingService() {
            return new FakeEmbeddingService();
        }
    }

    static class FakeEmbeddingService implements EmbeddingService {
        List<String> lastTexts = List.of();

        void reset() {
            lastTexts = List.of();
        }

        @Override
        public List<float[]> embedBatch(List<String> texts) {
            lastTexts = List.copyOf(texts);
            return texts.stream().map(text -> new float[]{text.length(), 1f, 0f, 0f}).toList();
        }

        @Override public String modelName() { return "fake-embedding"; }
        @Override public int dimension() { return 4; }
    }

    static class FakeVectorStore implements VectorStoreService {
        private final Map<String, List<VectorPoint>> points = new HashMap<>();
        final AtomicBoolean failReplace = new AtomicBoolean();
        final AtomicBoolean failSearch = new AtomicBoolean();
        Long lastUserId;
        Long lastKnowledgeBaseId;

        void reset() {
            points.clear();
            failReplace.set(false);
            failSearch.set(false);
            lastUserId = null;
            lastKnowledgeBaseId = null;
        }

        @Override public void ensureCollection() { }

        @Override
        public void replaceDocumentPoints(Long userId, Long knowledgeBaseId, Long documentId,
                                          List<VectorPoint> replacement) {
            if (failReplace.get()) throw new BusinessException(503, "测试 Qdrant 不可用");
            points.put(key(userId, knowledgeBaseId, documentId), new ArrayList<>(replacement));
        }

        @Override
        public void deleteDocumentPoints(Long userId, Long knowledgeBaseId, Long documentId) {
            points.remove(key(userId, knowledgeBaseId, documentId));
        }

        @Override
        public void deleteKnowledgeBasePoints(Long userId, Long knowledgeBaseId) {
            points.keySet().removeIf(key -> key.startsWith(userId + ":" + knowledgeBaseId + ":"));
        }

        @Override
        public List<VectorSearchResult> similaritySearch(Long userId, Long knowledgeBaseId,
                                                         float[] queryVector, int topK) {
            if (failSearch.get()) throw new BusinessException(503, "测试 Qdrant 不可用");
            lastUserId = userId;
            lastKnowledgeBaseId = knowledgeBaseId;
            return points.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(userId + ":" + knowledgeBaseId + ":"))
                    .flatMap(entry -> entry.getValue().stream())
                    .limit(topK)
                    .map(point -> new VectorSearchResult(point.chunkId(), point.documentId(),
                            point.chunkIndex(), point.content(), 0.95f))
                    .toList();
        }

        @Override public String collectionName() { return "test-knowledge-chunks"; }
        @Override public int dimension() { return 4; }

        List<VectorPoint> documentPoints(Long userId, Long knowledgeBaseId, Long documentId) {
            return points.getOrDefault(key(userId, knowledgeBaseId, documentId), List.of());
        }

        int totalPoints() {
            return points.values().stream().mapToInt(List::size).sum();
        }

        private String key(Long userId, Long knowledgeBaseId, Long documentId) {
            return userId + ":" + knowledgeBaseId + ":" + documentId;
        }
    }
}
