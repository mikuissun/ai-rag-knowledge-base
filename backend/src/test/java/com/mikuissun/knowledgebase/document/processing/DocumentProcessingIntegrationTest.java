package com.mikuissun.knowledgebase.document.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.auth.JwtTokenService;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.document.embedding.EmbeddingService;
import com.mikuissun.knowledgebase.document.entity.Document;
import com.mikuissun.knowledgebase.document.mapper.DocumentMapper;
import com.mikuissun.knowledgebase.user.entity.User;
import com.mikuissun.knowledgebase.user.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.rag.chunk-size=100",
        "app.rag.chunk-overlap=20",
        "app.rag.max-chunks-per-document=20"
})
@AutoConfigureMockMvc
@ContextConfiguration(classes = {
        com.mikuissun.knowledgebase.KnowledgeBaseApplication.class,
        DocumentProcessingIntegrationTest.EmbeddingTestConfig.class
})
class DocumentProcessingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper users;
    @Autowired DocumentMapper documents;
    @Autowired JwtTokenService tokens;
    @Autowired FakeEmbeddingService fakeEmbedding;
    User userA;
    User userB;
    String tokenA;
    String tokenB;
    long kbA;
    long kbB;
    long secondKbA;

    @BeforeEach
    void setup() throws Exception {
        fakeEmbedding.fail.set(false);
        userA = user();
        userB = user();
        tokenA = tokens.generateToken(userA);
        tokenB = tokens.generateToken(userB);
        kbA = knowledgeBase(tokenA);
        kbB = knowledgeBase(tokenB);
        secondKbA = knowledgeBase(tokenA);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM documents WHERE user_id IN (?, ?)", userA.getId(), userB.getId());
        jdbc.update("DELETE FROM knowledge_bases WHERE user_id IN (?, ?)", userA.getId(), userB.getId());
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", userA.getId(), userB.getId());
    }

    @Test
    void processingPersistsChunksAndEmbeddingsAndIsIdempotent() throws Exception {
        long documentId = document(userA.getId(), kbA, longChineseText());
        String endpoint = endpoint(kbA, documentId);

        String first = mvc.perform(post(endpoint).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId))
                .andExpect(jsonPath("$.data.embeddingModel").value("fake-embedding"))
                .andExpect(jsonPath("$.data.processingStatus").value("PROCESSED"))
                .andReturn().getResponse().getContentAsString();
        int count = json.readTree(first).path("data").path("chunkCount").asInt();
        assertThat(count).isGreaterThan(1);
        assertThat(json.readTree(first).path("data").path("embeddingCount").asInt()).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_chunks WHERE document_id=?",
                Integer.class, documentId)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chunk_embeddings e JOIN document_chunks c "
                + "ON c.id=e.chunk_id WHERE c.document_id=?", Integer.class, documentId)).isEqualTo(count);
        assertThat(jdbc.queryForList("SELECT chunk_index FROM document_chunks WHERE document_id=? "
                + "ORDER BY chunk_index", Integer.class, documentId))
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, count).boxed().toList());
        assertThat(jdbc.queryForList("SELECT dimension FROM chunk_embeddings e JOIN document_chunks c "
                + "ON c.id=e.chunk_id WHERE c.document_id=?", Integer.class, documentId)).containsOnly(4);
        List<Long> firstIds = jdbc.queryForList(
                "SELECT id FROM document_chunks WHERE document_id=? ORDER BY chunk_index", Long.class, documentId);

        mvc.perform(post(endpoint).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chunkCount").value(count))
                .andExpect(jsonPath("$.data.embeddingCount").value(count));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_chunks WHERE document_id=?",
                Integer.class, documentId)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chunk_embeddings e JOIN document_chunks c "
                + "ON c.id=e.chunk_id WHERE c.document_id=?", Integer.class, documentId)).isEqualTo(count);
        assertThat(jdbc.queryForList("SELECT id FROM document_chunks WHERE document_id=? ORDER BY chunk_index",
                Long.class, documentId)).doesNotContainAnyElementsOf(firstIds);
        assertThat(jdbc.queryForObject("SELECT processing_status FROM documents WHERE id=?",
                String.class, documentId)).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT processed_at IS NOT NULL FROM documents WHERE id=?",
                Boolean.class, documentId)).isTrue();
    }

    @Test
    void rejectsCrossUserAndKnowledgeBaseMismatch() throws Exception {
        long documentA = document(userA.getId(), kbA, "属于用户 A 的正文");
        mvc.perform(post(endpoint(kbA, documentA)).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(post(endpoint(kbB, documentA)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        mvc.perform(post(endpoint(secondKbA, documentA)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        mvc.perform(post(endpoint(kbA, documentA))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT processing_status FROM documents WHERE id=?",
                String.class, documentA)).isEqualTo("PENDING");
    }

    @Test
    void embeddingFailureMarksDocumentFailedWithoutReplacingOldData() throws Exception {
        long documentId = document(userA.getId(), kbA, longChineseText());
        mvc.perform(post(endpoint(kbA, documentId)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        int oldChunks = jdbc.queryForObject("SELECT COUNT(*) FROM document_chunks WHERE document_id=?",
                Integer.class, documentId);

        fakeEmbedding.fail.set(true);
        mvc.perform(post(endpoint(kbA, documentId)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value(502));
        assertThat(jdbc.queryForObject("SELECT processing_status FROM documents WHERE id=?",
                String.class, documentId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT processing_error FROM documents WHERE id=?",
                String.class, documentId)).isEqualTo("测试 Embedding 失败");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_chunks WHERE document_id=?",
                Integer.class, documentId)).isEqualTo(oldChunks);
    }

    @Test
    void deletingDocumentCascadesChunksAndEmbeddings() throws Exception {
        long documentId = document(userA.getId(), kbA, longChineseText());
        mvc.perform(post(endpoint(kbA, documentId)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_chunks WHERE document_id=?",
                Integer.class, documentId)).isPositive();
        List<Long> chunkIds = jdbc.queryForList(
                "SELECT id FROM document_chunks WHERE document_id=?", Long.class, documentId);

        mvc.perform(delete("/api/knowledge-bases/{kb}/documents/{doc}", kbA, documentId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_chunks WHERE document_id=?",
                Integer.class, documentId)).isZero();
        for (Long chunkId : chunkIds) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chunk_embeddings WHERE chunk_id=?",
                    Integer.class, chunkId)).isZero();
        }
        assertThat(documents.selectById(documentId)).isNull();
    }

    private User user() {
        User user = new User();
        user.setUsername("process" + UUID.randomUUID().toString().replace("-", ""));
        user.setPassword("unused-test-only-hash");
        user.setStatus(1);
        users.insert(user);
        return user;
    }

    private long knowledgeBase(String token) throws Exception {
        String response = mvc.perform(post("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"name\":\"Processing test\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("id").asLong();
    }

    private long document(long userId, long knowledgeBaseId, String content) {
        Document document = new Document();
        document.setUserId(userId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setOriginalName("processing-test.txt");
        document.setStoredName(UUID.randomUUID() + ".txt");
        document.setFilePath(userId + "/" + knowledgeBaseId + "/missing-test-file.txt");
        document.setFileType("txt");
        document.setFileSize((long) content.length());
        document.setContentText(content);
        document.setStatus(1);
        documents.insert(document);
        return document.getId();
    }

    private String longChineseText() {
        return "企业知识库通过文档解析获得正文。文本切分优先保留自然语义边界。"
                + "相邻文本块包含重叠内容，有助于后续语义检索保持上下文连续。\n\n"
                + "Embedding 将每个文本块转换成固定维度向量。本阶段把向量暂存到 MySQL，"
                + "只验证完整处理链路，不实现向量检索、Qdrant 或 RAG 问答。"
                + "重复处理采用原子替换，确保不会不断累积重复数据。";
    }

    private String endpoint(long knowledgeBaseId, long documentId) {
        return "/api/knowledge-bases/" + knowledgeBaseId + "/documents/" + documentId + "/process";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EmbeddingTestConfig {
        @Bean
        @Primary
        FakeEmbeddingService fakeEmbeddingService() {
            return new FakeEmbeddingService();
        }
    }

    static class FakeEmbeddingService implements EmbeddingService {
        private final AtomicBoolean fail = new AtomicBoolean();
        @Override
        public List<float[]> embedBatch(List<String> texts) {
            if (fail.get()) throw new BusinessException(502, "测试 Embedding 失败");
            return texts.stream().map(text -> new float[]{text.length(), 0.25f, 0.5f, 1f}).toList();
        }
        @Override public String modelName() { return "fake-embedding"; }
        @Override public int dimension() { return 4; }
    }
}
