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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
class IncidentEventControllerIntegrationTest extends IntegrationTestBase {

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
    void authenticationAndMembership() throws Exception {
        User owner = createUser("evt-owner@example.com");
        User member = createUser("evt-member@example.com");
        User viewer = createUser("evt-viewer@example.com");
        User outsider = createUser("evt-outsider@example.com");

        String ownerToken = login("evt-owner@example.com");
        String memberToken = login("evt-member@example.com");
        String viewerToken = login("evt-viewer@example.com");
        String outsiderToken = login("evt-outsider@example.com");

        String orgId = createOrganization(ownerToken, "Events Co", "evt-" + UUID.randomUUID().toString().substring(0, 8));
        addMember(ownerToken, orgId, member.getId(), "MEMBER");
        addMember(ownerToken, orgId, viewer.getId(), "VIEWER");

        String incidentId = createIncident(memberToken, orgId, "Auth test", "SEV2");

        mockMvc.perform(get(eventsPath(orgId, incidentId))).andExpect(status().isUnauthorized());

        mockMvc.perform(get(eventsPath(orgId, incidentId)).header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(eventsPath(orgId, incidentId)).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(eventsPath(orgId, incidentId)).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(eventsPath(orgId, incidentId)).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void tenantIsolation() throws Exception {
        User userA = createUser("evt-a@example.com");
        User userB = createUser("evt-b@example.com");
        String tokenA = login("evt-a@example.com");
        String tokenB = login("evt-b@example.com");

        String orgA = createOrganization(tokenA, "Org A", "org-a-" + UUID.randomUUID().toString().substring(0, 8));
        String orgB = createOrganization(tokenB, "Org B", "org-b-" + UUID.randomUUID().toString().substring(0, 8));

        String incidentA = createIncident(tokenA, orgA, "Incident A", "SEV2");
        String incidentB = createIncident(tokenB, orgB, "Incident B", "SEV3");

        mockMvc.perform(get(eventsPath(orgA, incidentB)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(eventsPath(orgB, incidentA)).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        MvcResult resultA = mockMvc.perform(get(eventsPath(orgA, incidentA)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        assertNoIncidentIdsInResponse(resultA, incidentB);
    }

    @Test
    void eventContentAndActor() throws Exception {
        User owner = createUser("content-owner@example.com");
        User member = createUser("content-member@example.com");
        String ownerToken = login("content-owner@example.com");
        String memberToken = login("content-member@example.com");

        String orgId = createOrganization(ownerToken, "Content", "content-" + UUID.randomUUID().toString().substring(0, 8));
        addMember(ownerToken, orgId, member.getId(), "MEMBER");

        String incidentId = createIncident(memberToken, orgId, "Timeline", "SEV2");

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\":\"SEV1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(eventsPath(orgId, incidentId)).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.content[0].payload.old").value("OPEN"))
                .andExpect(jsonPath("$.content[0].payload.new").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.content[0].actorId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.content[1].type").value("SEVERITY_CHANGED"))
                .andExpect(jsonPath("$.content[1].payload.old").value("SEV2"))
                .andExpect(jsonPath("$.content[1].payload.new").value("SEV1"))
                .andExpect(jsonPath("$.content[1].actorId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.content[2].type").value("INCIDENT_CREATED"))
                .andExpect(jsonPath("$.content[2].actorId").value(member.getId().toString()))
                .andExpect(jsonPath("$.content[2].incidentId").value(incidentId))
                .andExpect(jsonPath("$.content[2].organizationId").value(orgId))
                .andExpect(jsonPath("$.content[0].id").isNotEmpty())
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty());
    }

    @Test
    void orderingIsNewestFirst() throws Exception {
        User owner = createUser("order-owner@example.com");
        String ownerToken = login("order-owner@example.com");
        String orgId = createOrganization(ownerToken, "Order", "order-" + UUID.randomUUID().toString().substring(0, 8));

        String incidentId = createIncident(ownerToken, orgId, "Order", "SEV3");

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"One\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Two\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Three\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get(eventsPath(orgId, incidentId))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andReturn();

        List<String> types = extractJsonPathArray(result, "type");
        assertThatNewestTitleEventsFirst(types);
    }

    @Test
    void pagination() throws Exception {
        User owner = createUser("page-owner@example.com");
        String ownerToken = login("page-owner@example.com");
        String orgId = createOrganization(ownerToken, "Page", "page-" + UUID.randomUUID().toString().substring(0, 8));

        String incidentId = createIncident(ownerToken, orgId, "Page", "SEV4");

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"title\":\"Title %d\"}", i)))
                    .andExpect(status().isOk());
        }

        MvcResult page0 = mockMvc.perform(get(eventsPath(orgId, incidentId))
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn();

        MvcResult page1 = mockMvc.perform(get(eventsPath(orgId, incidentId))
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn();

        Set<String> ids = new HashSet<>();
        ids.addAll(extractJsonPathArray(page0, "id"));
        ids.addAll(extractJsonPathArray(page1, "id"));
        org.assertj.core.api.Assertions.assertThat(ids).hasSize(4);

        mockMvc.perform(get(eventsPath(orgId, incidentId))
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void unknownIncidentReturnsForbidden() throws Exception {
        User owner = createUser("missing-owner@example.com");
        String ownerToken = login("missing-owner@example.com");
        String orgId = createOrganization(ownerToken, "Missing", "missing-" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(get(eventsPath(orgId, UUID.randomUUID().toString()))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    private static void assertThatNewestTitleEventsFirst(List<String> types) {
        int firstTitle = types.indexOf("TITLE_CHANGED");
        int lastTitle = types.lastIndexOf("TITLE_CHANGED");
        if (firstTitle < 0 || lastTitle < 0) {
            throw new AssertionError("Expected TITLE_CHANGED events");
        }
        org.assertj.core.api.Assertions.assertThat(firstTitle).isLessThan(lastTitle);
        org.assertj.core.api.Assertions.assertThat(types.get(0)).isEqualTo("TITLE_CHANGED");
        org.assertj.core.api.Assertions.assertThat(types.get(types.size() - 1)).isEqualTo("INCIDENT_CREATED");
    }

    private static void assertNoIncidentIdsInResponse(MvcResult result, String forbiddenIncidentId) throws Exception {
        String json = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(json).doesNotContain(forbiddenIncidentId);
    }

    private static String eventsPath(String orgId, String incidentId) {
        return "/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/events";
    }

    private String createIncident(String token, String orgId, String title, String severity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/incidents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"title\":\"%s\",\"severity\":\"%s\"}", title, severity)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonString(result, "id");
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

    private static List<String> extractJsonPathArray(MvcResult result, String field) throws Exception {
        String json = result.getResponse().getContentAsString();
        Pattern contentBlock = Pattern.compile("\"content\"\\s*:\\s*\\[(.*)]", Pattern.DOTALL);
        Matcher contentMatcher = contentBlock.matcher(json);
        if (!contentMatcher.find()) {
            return List.of();
        }
        String content = contentMatcher.group(1);
        Pattern fieldPattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher fieldMatcher = fieldPattern.matcher(content);
        List<String> values = new ArrayList<>();
        while (fieldMatcher.find()) {
            values.add(fieldMatcher.group(1));
        }
        return values;
    }
}
