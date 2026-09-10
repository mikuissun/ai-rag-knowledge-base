package com.mikuissun.knowledgebase.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.auth.JwtTokenService;
import com.mikuissun.knowledgebase.user.entity.User;
import com.mikuissun.knowledgebase.user.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DocumentIntegrationTest {
    @TempDir static Path root;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.base-path", () -> root.toString());
        registry.add("app.jwt.secret", () -> UUID.randomUUID().toString() + UUID.randomUUID());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper users;
    @Autowired JwtTokenService tokens;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    User userA;
    User userB;
    String tokenA;
    String tokenB;
    long kbA;
    long kbB;
    long secondKbA;

    @BeforeEach
    void setup() throws Exception {
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

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "docx", "md", "txt"})
    void uploadListDetailDeleteAndIsolation(String extension) throws Exception {
        var file = new MockMultipartFile("file", "../../sample." + extension, "application/octet-stream",
                DocumentServiceTest.fixture(extension));
        String response = mvc.perform(multipart(url(kbA)).file(file).param("userId", userB.getId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contentText").isNotEmpty())
                .andExpect(jsonPath("$.data.filePath").doesNotExist())
                .andExpect(jsonPath("$.data.storedName").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).path("data").path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT user_id FROM documents WHERE id=?", Long.class, id))
                .isEqualTo(userA.getId());
        Path saved = root.resolve(jdbc.queryForObject("SELECT file_path FROM documents WHERE id=?", String.class, id));
        assertThat(saved).exists();
        mvc.perform(get(url(kbA)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].contentText").doesNotExist());
        mvc.perform(get(url(kbA) + "/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contentText").isNotEmpty());
        mvc.perform(get(url(kbB)).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(multipart(url(kbB)).file(file).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        mvc.perform(get(url(kbA)).header("Authorization", "Bearer " + tokenB)).andExpect(status().isNotFound());
        mvc.perform(get(url(kbA) + "/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mvc.perform(delete(url(kbA) + "/" + id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        for (long wrongKb : new long[]{kbB, secondKbA}) {
            mvc.perform(get(url(wrongKb) + "/" + id).header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(url(wrongKb) + "/" + id).header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/knowledge-bases/" + kbA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isConflict());
        assertThat(saved).exists();
        mvc.perform(delete(url(kbA) + "/" + id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        assertThat(saved).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE id=?", Integer.class, id)).isZero();
        mvc.perform(delete("/api/knowledge-bases/" + kbA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingAuthFilesAndInvalidContent() throws Exception {
        var valid = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{65});
        mvc.perform(multipart(url(kbA)).file(valid)).andExpect(status().isUnauthorized());
        mvc.perform(get(url(kbA))).andExpect(status().isUnauthorized());
        mvc.perform(get(url(kbA) + "/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete(url(kbA) + "/1")).andExpect(status().isUnauthorized());
        mvc.perform(multipart(url(kbA)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(url(Long.MAX_VALUE)).file(valid).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        for (String name : new String[]{"a.exe", "a.pdf", "a.docx"}) {
            mvc.perform(multipart(url(kbA)).file(new MockMultipartFile("file", name,
                            "application/octet-stream", new byte[]{1, 2, 3}))
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(multipart(url(kbA)).file(new MockMultipartFile("file", "a.txt", "text/plain",
                        new byte[20 * 1024 * 1024 + 1])).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value(413));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE user_id=?", Integer.class, userA.getId())).isZero();
    }

    private User user() {
        var user = new User();
        user.setUsername("doc" + UUID.randomUUID().toString().replace("-", ""));
        user.setPassword("unused-test-only-hash");
        user.setStatus(1);
        users.insert(user);
        return user;
    }

    @Test
    void realHttpMultipartEnforcesSizeLimit() throws Exception {
        var client = java.net.http.HttpClient.newHttpClient();
        String boundary = "stage4-" + UUID.randomUUID();
        for (int size : new int[]{5, 20 * 1024 * 1024 + 1}) {
            var body = new java.io.ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.txt\""
                    + "\r\nContent-Type: text/plain\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] content = new byte[size];
            java.util.Arrays.fill(content, (byte) 'a');
            body.write(content);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var request = java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://localhost:" + port + url(kbA)))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + tokenA)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(size == 5 ? 200 : 413);
            assertThat(json.readTree(response.body()).path("code").asInt()).isEqualTo(response.statusCode());
        }
    }
    private long knowledgeBase(String token) throws Exception {
        String result = mvc.perform(post("/api/knowledge-bases").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"name\":\"Document test\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).path("data").path("id").asLong();
    }
    private String url(long id) { return "/api/knowledge-bases/" + id + "/documents"; }
}
