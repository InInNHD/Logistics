package com.firefly.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.auth.domain.UserAccount;
import com.firefly.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest @AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository repository;
    @Autowired PasswordEncoder encoder;

    @BeforeEach void resetAdminLock() {
        repository.findByUsername("admin").ifPresent(user -> {
            user.loginSucceeded();
            user.updateProfile(null, "ADMIN", true);
            repository.save(user);
        });
    }

    @Test void logsInWithSeededAdmin() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"Firefly@123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test void returnsUnifiedValidationErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请求字段校验失败")));
    }

    @Test void locksAccountAfterRepeatedFailures() throws Exception {
        for (int attempt = 1; attempt <= 2; attempt++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Firefly@123\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test void adminCanCreateListAndUpdateUsers() throws Exception {
        String token = login("admin", "Firefly@123");
        String username = "receiver_" + System.nanoTime();
        String createResponse = mvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Strong@123\",\"displayName\":\"收货测试员\",\"roles\":[\"RECEIVER\"],\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("RECEIVER"))
                .andReturn().getResponse().getContentAsString();
        Long userId = objectMapper.readTree(createResponse).path("data").path("id").longValue();

        mvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", username)
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value(username));

        mvc.perform(patch("/api/auth/users/{id}", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"拣货测试员\",\"roles\":[\"PICKER\"],\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("PICKER"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test void nonAdminCannotManageUsers() throws Exception {
        String username = "picker_" + System.nanoTime();
        repository.save(new UserAccount(username, encoder.encode("Strong@123"), "拣货员", "PICKER"));
        String token = login(username, "Strong@123");

        mvc.perform(get("/api/auth/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test void meRequiresJwtAndReturnsStoredDisplayName() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        String token = login("admin", "Firefly@123");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("系统管理员"));
    }

    @Test void staleAdminTokenCannotManageUsersOrRestoreItself() throws Exception {
        UserAccount primary = repository.findByUsername("admin").orElseThrow();
        String staleToken = login("admin", "Firefly@123");
        String secondUsername = "admin_guard_" + System.nanoTime();
        UserAccount second = repository.saveAndFlush(
                new UserAccount(secondUsername, encoder.encode("Strong@123"), "安全管理员", "ADMIN"));
        String secondToken = login(secondUsername, "Strong@123");

        try {
            mvc.perform(patch("/api/auth/users/{id}", primary.getId())
                            .header("Authorization", "Bearer " + secondToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roles\":[\"PICKER\"],\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/auth/users").header("Authorization", "Bearer " + staleToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
            mvc.perform(post("/api/auth/users")
                            .header("Authorization", "Bearer " + staleToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"forbidden_user\",\"password\":\"Strong@123\",\"displayName\":\"不应创建\",\"roles\":[\"RECEIVER\"],\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/auth/users/{id}", primary.getId())
                            .header("Authorization", "Bearer " + staleToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roles\":[\"ADMIN\"],\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isForbidden());
            assertEquals("PICKER", repository.findById(primary.getId()).orElseThrow().getRole());

            UserAccount restored = repository.findById(primary.getId()).orElseThrow();
            restored.updateProfile(null, "ADMIN", true);
            repository.saveAndFlush(restored);
            mvc.perform(patch("/api/auth/users/{id}", primary.getId())
                            .header("Authorization", "Bearer " + secondToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"DISABLED\"}"))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/auth/roles").header("Authorization", "Bearer " + staleToken))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/auth/users/{id}", primary.getId())
                            .header("Authorization", "Bearer " + staleToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roles\":[\"ADMIN\"],\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isForbidden());
            assertFalse(repository.findById(primary.getId()).orElseThrow().isEnabled());
        } finally {
            UserAccount restored = repository.findById(primary.getId()).orElseThrow();
            restored.updateProfile(null, "ADMIN", true);
            repository.saveAndFlush(restored);
            repository.deleteById(second.getId());
        }
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(username, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private record LoginPayload(String username, String password) {}
}
