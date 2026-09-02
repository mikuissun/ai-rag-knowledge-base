package com.mikuissun.knowledgebase.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikuissun.knowledgebase.config.JwtProperties;
import com.mikuissun.knowledgebase.user.entity.User;
import com.mikuissun.knowledgebase.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String TEST_JWT_SECRET = UUID.randomUUID() + UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String username;
    private String disabledUsername;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.jwt.expiration-seconds", () -> 3600);
    }

    @BeforeEach
    void prepareTestUsernames() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        username = "stage2test" + suffix;
        disabledUsername = "stage2disabled" + suffix;
    }

    @AfterEach
    void removeTestUsers() {
        jdbcTemplate.update("DELETE FROM users WHERE username IN (?, ?)", username, disabledUsername);
    }

    @Test
    void shouldMigrateUsersTableAndCompleteAuthenticationFlow() throws Exception {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'users'",
                Integer.class);
        assertThat(tableCount).isEqualTo(1);

        String request = objectMapper.writeValueAsString(java.util.Map.of(
                "username", username,
                "password", "password123",
                "nickname", "Stage Two",
                "email", "stage2@example.com"));
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn();

        assertThat(registration.getResponse().getContentAsString()).doesNotContain("password123");
        User savedUser = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", savedUser.getPassword())).isTrue();

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andReturn();

        JsonNode response = objectMapper.readTree(login.getResponse().getContentAsString());
        String token = response.path("data").path("token").asText();
        assertThat(jwtTokenService.parseAndValidate(token))
                .contains(new JwtTokenService.TokenPayload(savedUser.getId(), username));
        assertThat(jwtTokenService.parseAndValidate("invalid-token")).isEmpty();
    }

    @Test
    void shouldRejectInvalidCredentialsDisabledUsersAndInvalidRequests() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"missing_user\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"ab\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of("username", disabledUsername, "password", "password123"))))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE users SET status = 0 WHERE username = ?", disabledUsername);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of("username", disabledUsername, "password", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtTokenService expiredTokenService = new JwtTokenService(new JwtProperties(TEST_JWT_SECRET, -1));
        User user = new User();
        user.setId(1L);
        user.setUsername("expired_user");
        assertThat(expiredTokenService.parseAndValidate(expiredTokenService.generateToken(user))).isEmpty();
    }
}
