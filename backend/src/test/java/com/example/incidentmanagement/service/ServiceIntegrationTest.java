package com.example.incidentmanagement.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import com.example.incidentmanagement.user.UserRole;
import java.util.UUID;
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
class ServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMockMvc(context);
        databaseCleaner.cleanAll();
    }

    @Test
    void serviceCatalogAuthorizationAndLifecycle() throws Exception {
        User owner = createUser("owner@example.com");
        User admin = createUser("admin@example.com");
        User member = createUser("member@example.com");
        User viewer = createUser("viewer@example.com");
        User outsider = createUser("outsider@example.com");

        String ownerToken = login("owner@example.com");
        String adminToken = login("admin@example.com");
        String memberToken = login("member@example.com");
        String viewerToken = login("viewer@example.com");
        String outsiderToken = login("outsider@example.com");

        String orgId = createOrganization(ownerToken, "Acme", "acme");
        addMember(ownerToken, orgId, admin.getId(), "ADMIN");
        addMember(ownerToken, orgId, member.getId(), "MEMBER");
        addMember(ownerToken, orgId, viewer.getId(), "VIEWER");

        String otherOrgId = createOrganization(outsiderToken, "Other", "other");

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Payments\",\"slug\":\"payments\",\"description\":\"Pay\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("payments"))
                .andExpect(jsonPath("$.teamId").isEmpty());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Billing\",\"slug\":\"billing\"}"))
                .andExpect(status().isCreated());

        MvcResult paymentsResult = mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        String serviceId = extractJsonStringFromArray(paymentsResult, "payments", "id");

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services/" + serviceId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Payments"));

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneak\",\"slug\":\"sneak\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/services/" + serviceId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/organizations/" + orgId + "/services/" + serviceId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services/" + serviceId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/organizations/" + otherOrgId + "/services/" + serviceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Payments\",\"slug\":\"payments-dup\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other Name\",\"slug\":\"payments\"}"))
                .andExpect(status().isConflict());

        String otherOrgService = createOrganization(ownerToken, "Beta", "beta");
        mockMvc.perform(post("/api/v1/organizations/" + otherOrgService + "/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Payments\",\"slug\":\"payments\"}"))
                .andExpect(status().isCreated());

        MvcResult teamResult = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Platform\",\"description\":\"Core\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String teamId = extractJsonString(teamResult, "id");

        MvcResult otherTeamResult = mockMvc.perform(post("/api/v1/organizations/" + otherOrgId + "/teams")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"External\",\"description\":\"X\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String otherTeamId = extractJsonString(otherTeamResult, "id");

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"With Team\",\"slug\":\"with-team\",\"teamId\":\"%s\"}", teamId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamName").value("Platform"));

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"Bad Team\",\"slug\":\"bad-team\",\"teamId\":\"%s\"}", otherTeamId)))
                .andExpect(status().isForbidden());

        MvcResult withTeamResult = mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String withTeamServiceId = extractJsonStringFromArray(withTeamResult, "with-team", "id");

        mockMvc.perform(delete("/api/v1/organizations/" + orgId + "/teams/" + teamId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services/" + withTeamServiceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").isEmpty())
                .andExpect(jsonPath("$.teamName").isEmpty());
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(UserRole.ENGINEER);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"Password1\"}", email)))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonString(result, "accessToken");
    }

    private String createOrganization(String token, String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"slug\":\"%s\"}", name, slug)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonString(result, "id");
    }

    private void addMember(String ownerToken, String orgId, UUID userId, String role) throws Exception {
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"userId\":\"%s\",\"role\":\"%s\"}", userId, role)))
                .andExpect(status().isCreated());
    }

    private static String extractJsonString(MvcResult result, String field) throws Exception {
        String json = result.getResponse().getContentAsString();
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Field not found: " + field);
        }
        return matcher.group(1);
    }

    private static String extractJsonStringFromArray(MvcResult result, String slug, String field) throws Exception {
        String json = result.getResponse().getContentAsString();
        Pattern blockPattern = Pattern.compile(
                "\\{[^{}]*\"slug\"\\s*:\\s*\"" + slug + "\"[^{}]*\\}", Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(json);
        if (!blockMatcher.find()) {
            throw new IllegalStateException("Object with slug not found: " + slug);
        }
        String block = blockMatcher.group();
        Pattern fieldPattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher fieldMatcher = fieldPattern.matcher(block);
        if (!fieldMatcher.find()) {
            throw new IllegalStateException("Field not found in block: " + field);
        }
        return fieldMatcher.group(1);
    }
}
