package com.mikuissun.knowledgebase.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mikuissun.knowledgebase.auth.JwtTokenService;
import com.mikuissun.knowledgebase.knowledge.entity.KnowledgeBase;
import com.mikuissun.knowledgebase.knowledge.mapper.KnowledgeBaseMapper;
import com.mikuissun.knowledgebase.user.entity.User;
import com.mikuissun.knowledgebase.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeBaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void createUsers() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userA = createUser("kbtesta" + suffix);
        userB = createUser("kbtestb" + suffix);
        tokenA = jwtTokenService.generateToken(userA);
        tokenB = jwtTokenService.generateToken(userB);
    }

    @AfterEach
    void removeTestData() {
        jdbcTemplate.update("DELETE FROM knowledge_bases WHERE user_id IN (?, ?)", userA.getId(), userB.getId());
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", userA.getId(), userB.getId());
    }

    @Test
    void shouldMigrateKnowledgeBasesAndEnforceUserIsolation() throws Exception {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'knowledge_bases'",
                Integer.class);
        assertThat(tableCount).isEqualTo(1);

        Long knowledgeBaseAId = createKnowledgeBase(tokenA, "知识库 A", "仅属于用户 A");
        Long knowledgeBaseBId = createKnowledgeBase(tokenB, "知识库 B", "仅属于用户 B");

        KnowledgeBase storedA = knowledgeBaseMapper.selectById(knowledgeBaseAId);
        assertThat(storedA.getUserId()).isEqualTo(userA.getId());

        mockMvc.perform(get("/api/knowledge-bases").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(knowledgeBaseAId));

        mockMvc.perform(get("/api/knowledge-bases/{id}", knowledgeBaseAId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("知识库 A"));

        mockMvc.perform(get("/api/knowledge-bases/{id}", knowledgeBaseBId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(put("/api/knowledge-bases/{id}", knowledgeBaseBId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{\"name\":\"越权修改\",\"description\":\"不应成功\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/knowledge-bases/{id}", knowledgeBaseBId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/knowledge-bases/{id}", knowledgeBaseAId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{\"name\":\"知识库 A 已更新\",\"description\":\"更新后的描述\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("知识库 A 已更新"));

        mockMvc.perform(delete("/api/knowledge-bases/{id}", knowledgeBaseAId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        assertThat(knowledgeBaseMapper.selectById(knowledgeBaseAId)).isNull();
        assertThat(knowledgeBaseMapper.selectById(knowledgeBaseBId)).isNotNull();
    }

    @Test
    void shouldRejectUnauthenticatedAndInvalidRequests() throws Exception {
        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{\"name\":\"  \",\"description\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        String tooLongName = "a".repeat(101);
        mockMvc.perform(post("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{\"name\":\"" + tooLongName + "\"}"))
                .andExpect(status().isBadRequest());

        String tooLongDescription = "a".repeat(501);
        mockMvc.perform(post("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{\"name\":\"有效名称\",\"description\":\"" + tooLongDescription + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private Long createKnowledgeBase(String token, String name, String description) throws Exception {
        String response = mockMvc.perform(post("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data").path("id").asLong();
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }
}
