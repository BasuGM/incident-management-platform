package com.example.incidentmanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import com.example.incidentmanagement.user.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(context);
        databaseCleaner.cleanAll();
    }

    @Test
    void registerLoginRefreshLogoutAndCurrentUser() throws Exception {
        registerUser("engineer@example.com", "Password1", "Eng", "User");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"email":"engineer@example.com","password":"Password1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("ENGINEER"))
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();

        String accessToken = extractJsonString(loginResult, "accessToken");
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("engineer@example.com"));

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String refreshedAccess = extractJsonString(refreshResult, "accessToken");
        Cookie rotatedCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertThat(rotatedCookie).isNotNull();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(rotatedCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + refreshedAccess))
                .andExpect(status().isOk());
    }

    @Test
    void authorizationRules() throws Exception {
        registerUser("engineer@example.com", "Password1", "Eng", "User");
        createAdmin("admin@example.com", "Password1");

        String engineerToken = loginAndGetAccessToken("engineer@example.com", "Password1");
        String adminToken = loginAndGetAccessToken("admin@example.com", "Password1");
        String engineerId = extractJsonString(
                mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + engineerToken))
                        .andReturn(),
                "id");

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/v1/users/" + engineerId).header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/users/" + engineerId)
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Updated\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/users/" + engineerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
    }

    private void registerUser(String email, String password, String firstName, String lastName)
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                """
                                {"email":"%s","password":"%s","firstName":"%s","lastName":"%s"}
                                """,
                                email, password, firstName, lastName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ENGINEER"));
    }

    private void createAdmin(String email, String password) {
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                """
                                {"email":"%s","password":"%s"}
                                """,
                                email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonString(result, "accessToken");
    }

    private static String extractJsonString(MvcResult result, String field) throws Exception {
        String json = result.getResponse().getContentAsString();
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Field not found in JSON: " + field);
        }
        return matcher.group(1);
    }
}
