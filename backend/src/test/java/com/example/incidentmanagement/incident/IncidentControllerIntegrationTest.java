package com.example.incidentmanagement.incident;

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
class IncidentControllerIntegrationTest extends IntegrationTestBase {

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
    void incidentApiAuthorizationCreateReadListAndUpdate() throws Exception {
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

        String orgId = createOrganization(ownerToken, "Acme", "acme-" + UUID.randomUUID().toString().substring(0, 8));
        String otherOrgId = createOrganization(outsiderToken, "Other", "other-" + UUID.randomUUID().toString().substring(0, 8));

        addMember(ownerToken, orgId, admin.getId(), "ADMIN");
        addMember(ownerToken, orgId, member.getId(), "MEMBER");
        addMember(ownerToken, orgId, viewer.getId(), "VIEWER");

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/incidents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"severity\":\"SEV2\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Viewer create\",\"severity\":\"SEV3\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"severity\":\"SEV2\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Missing severity\"}"))
                .andExpect(status().isBadRequest());

        MvcResult createResult = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"API outage\",\"description\":\"Errors\",\"severity\":\"SEV2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.incidentNumber").value(1))
                .andExpect(jsonPath("$.displayId").value("INC-1"))
                .andExpect(jsonPath("$.reporterId").value(member.getId().toString()))
                .andReturn();
        String incidentId = extractJsonString(createResult, "id");

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Payments\",\"slug\":\"payments\"}"))
                .andExpect(status().isCreated());
        MvcResult servicesResult = mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn();
        String serviceId = extractJsonStringFromArray(servicesResult, "payments", "id");

        String otherServiceId = createServiceInOrg(outsiderToken, otherOrgId, "external", "external");

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"title\":\"With service\",\"severity\":\"SEV3\",\"serviceId\":\"%s\"}", serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("Payments"));

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"title\":\"Bad service\",\"severity\":\"SEV4\",\"serviceId\":\"%s\"}",
                                otherServiceId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("API outage"));

        mockMvc.perform(get("/api/v1/organizations/" + otherOrgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/incidents")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("With service"));

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.description").value("Errors"));

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"serviceId\":\"%s\"}", serviceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(serviceId));

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").isEmpty());

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Too late\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidStatusTransitionReturnsConflict() throws Exception {
        User owner = createUser("status-owner@example.com");
        String ownerToken = login("status-owner@example.com");
        String orgId = createOrganization(ownerToken, "Status Co", "status-" + UUID.randomUUID().toString().substring(0, 8));

        MvcResult createResult = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Flow\",\"severity\":\"SEV2\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String incidentId = extractJsonString(createResult, "id");

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_INCIDENT_STATUS_TRANSITION"));
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

    private String createServiceInOrg(String token, String orgId, String name, String slug) throws Exception {
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"slug\":\"%s\"}", name, slug)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(get("/api/v1/organizations/" + orgId + "/services")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonStringFromArray(result, slug, "id");
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
