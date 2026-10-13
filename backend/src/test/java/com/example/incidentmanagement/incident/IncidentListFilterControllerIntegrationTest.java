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
class IncidentListFilterControllerIntegrationTest extends IntegrationTestBase {

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
    void unfilteredListReturnsAllStatuses() throws Exception {
        FilterFixture fixture = new FilterFixture();
        String openId = fixture.createIncident("Open one", "SEV2");
        String resolvedId = fixture.createIncident("Resolved one", "SEV2");
        fixture.resolveIncident(resolvedId);

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(openId));
    }

    @Test
    void activeFalseReturnsTerminalStatusesOnly() throws Exception {
        FilterFixture fixture = new FilterFixture();
        fixture.createIncident("Still open", "SEV2");
        String resolvedId = fixture.createIncident("Done", "SEV2");
        fixture.resolveIncident(resolvedId);
        String cancelledId = fixture.createIncident("Mistake", "SEV3");
        fixture.cancelIncident(cancelledId);

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void explicitStatusOverridesActive() throws Exception {
        FilterFixture fixture = new FilterFixture();
        fixture.createIncident("Open", "SEV2");
        String resolvedId = fixture.createIncident("Resolved", "SEV2");
        fixture.resolveIncident(resolvedId);

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("active", "true")
                        .param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("RESOLVED"));
    }

    @Test
    void severityServiceCommanderAndMineFilters() throws Exception {
        FilterFixture fixture = new FilterFixture();
        String serviceId = fixture.createService("api", "api");
        String mineId = fixture.createIncidentWithServiceAndCommander(
                "Mine", "SEV1", serviceId, fixture.member.getId());
        fixture.createIncidentWithServiceAndCommander("Other", "SEV1", serviceId, fixture.owner.getId());
        fixture.createIncident("Unassigned sev2", "SEV2");

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("severity", "SEV1")
                        .param("serviceId", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("commanderId", fixture.member.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mineId));

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("commanderId", "me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mineId));

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("mine", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mineId));
    }

    @Test
    void foreignServiceIdReturnsEmptyPage() throws Exception {
        FilterFixture fixture = new FilterFixture();
        String localService = fixture.createService("local", "local");
        fixture.createIncidentWithServiceAndCommander("Local", "SEV2", localService, null);

        User outsider = fixture.createUser("outsider-filter@example.com");
        String outsiderToken = fixture.login("outsider-filter@example.com");
        String otherOrg = fixture.createOrganization(outsiderToken, "Other Org", "other-org-filter");
        String foreignService = fixture.createServiceInOrg(outsiderToken, otherOrg, "Foreign", "foreign");

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("serviceId", foreignService))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchByTitleIncidentNumberAndCombinedFilters() throws Exception {
        FilterFixture fixture = new FilterFixture();
        String alpha = fixture.createIncident("Alpha checkout failure", "SEV2");
        fixture.createIncident("Beta issue", "SEV2");
        String numbered = fixture.createIncident("Gamma", "SEV3");

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("search", "checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(alpha));

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("search", "INC-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(numbered));

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("search", "3")
                        .param("severity", "SEV3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(numbered));

        mockMvc.perform(post("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Secret only\",\"description\":\"hidden description text\",\"severity\":\"SEV2\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("search", "description"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        FilterFixture fixture = new FilterFixture();
        fixture.createIncident("CamelCase Title", "SEV2");

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("search", "camelcase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void whitespaceSearchIgnoredReturnsAll() throws Exception {
        FilterFixture fixture = new FilterFixture();
        fixture.createIncident("One", "SEV2");
        fixture.createIncident("Two", "SEV2");

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("search", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void validationAndAuthorizationErrors() throws Exception {
        FilterFixture fixture = new FilterFixture();
        fixture.createIncident("One", "SEV2");

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("active", "maybe"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("serviceId", "not-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("mine", "true")
                        .param("commanderId", fixture.owner.getId().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Use mine or commanderId, not both"));

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("search", "x".repeat(201)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.viewerToken))
                .andExpect(status().isOk());

        User outsider = fixture.createUser("list-outsider@example.com");
        String outsiderToken = fixture.login("list-outsider@example.com");
        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginationMetadataMatchesFilteredResults() throws Exception {
        FilterFixture fixture = new FilterFixture();
        for (int i = 0; i < 3; i++) {
            fixture.createIncident("Open " + i, "SEV2");
        }
        String resolvedId = fixture.createIncident("Resolved", "SEV2");
        fixture.resolveIncident(resolvedId);

        mockMvc.perform(get("/api/v1/organizations/" + fixture.orgId + "/incidents")
                        .header("Authorization", "Bearer " + fixture.memberToken)
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    private final class FilterFixture {
        final User owner;
        final User member;
        final String ownerToken;
        final String memberToken;
        final String viewerToken;
        final String orgId;

        FilterFixture() throws Exception {
            owner = createUser("filter-owner@example.com");
            member = createUser("filter-member@example.com");
            User viewer = createUser("filter-viewer@example.com");
            ownerToken = login("filter-owner@example.com");
            memberToken = login("filter-member@example.com");
            viewerToken = login("filter-viewer@example.com");
            orgId = createOrganization(ownerToken, "Filter Org", "filter-org-" + UUID.randomUUID().toString().substring(0, 8));
            addMember(ownerToken, orgId, member.getId(), "MEMBER");
            addMember(ownerToken, orgId, viewer.getId(), "VIEWER");
        }

        String createIncident(String title, String severity) throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"title\":\"%s\",\"severity\":\"%s\"}", title, severity)))
                    .andExpect(status().isCreated())
                    .andReturn();
            return extractJsonString(result, "id");
        }

        String createIncidentWithServiceAndCommander(
                String title, String severity, String serviceId, UUID commanderId) throws Exception {
            String commanderJson = commanderId == null ? "null" : "\"" + commanderId + "\"";
            MvcResult result = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"title\":\"%s\",\"severity\":\"%s\",\"serviceId\":\"%s\",\"commanderId\":%s}",
                                    title, severity, serviceId, commanderJson)))
                    .andExpect(status().isCreated())
                    .andReturn();
            return extractJsonString(result, "id");
        }

        void resolveIncident(String incidentId) throws Exception {
            mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACKNOWLEDGED\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"RESOLVED\"}"))
                    .andExpect(status().isOk());
        }

        void cancelIncident(String incidentId) throws Exception {
            mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"CANCELLED\"}"))
                    .andExpect(status().isOk());
        }

        String createService(String name, String slug) throws Exception {
            return createServiceInOrg(ownerToken, orgId, name, slug);
        }

        User createUser(String email) {
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("Password1"));
            user.setFirstName("Test");
            user.setLastName("User");
            user.setRole(UserRole.ENGINEER);
            user.setEnabled(true);
            return userRepository.save(user);
        }

        String login(String email) throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"email\":\"%s\",\"password\":\"Password1\"}", email)))
                    .andExpect(status().isOk())
                    .andReturn();
            return extractJsonString(result, "accessToken");
        }

        String createOrganization(String token, String name, String slug) throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"name\":\"%s\",\"slug\":\"%s\"}", name, slug)))
                    .andExpect(status().isCreated())
                    .andReturn();
            return extractJsonString(result, "id");
        }

        void addMember(String ownerToken, String orgId, UUID userId, String role) throws Exception {
            mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"userId\":\"%s\",\"role\":\"%s\"}", userId, role)))
                    .andExpect(status().isCreated());
        }

        String createServiceInOrg(String token, String orgId, String name, String slug) throws Exception {
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
