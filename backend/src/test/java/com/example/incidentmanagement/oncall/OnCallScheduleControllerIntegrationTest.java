package com.example.incidentmanagement.oncall;

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
class OnCallScheduleControllerIntegrationTest extends IntegrationTestBase {

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
    void onCallScheduleApiLifecycle() throws Exception {
        User owner = createUser("oc-owner@example.com");
        User admin = createUser("oc-admin@example.com");
        User member = createUser("oc-member@example.com");
        User viewer = createUser("oc-viewer@example.com");
        User outsider = createUser("oc-outsider@example.com");

        String ownerToken = login("oc-owner@example.com");
        String adminToken = login("oc-admin@example.com");
        String memberToken = login("oc-member@example.com");
        String viewerToken = login("oc-viewer@example.com");
        String outsiderToken = login("oc-outsider@example.com");

        String orgId = createOrganization(ownerToken, "OnCall Org", "oncall-org");
        addMember(ownerToken, orgId, admin.getId(), "ADMIN");
        addMember(ownerToken, orgId, member.getId(), "MEMBER");
        addMember(ownerToken, orgId, viewer.getId(), "VIEWER");

        String createBody = String.format(
                """
                {
                  "name": "Primary",
                  "timezone": "UTC",
                  "rotationLengthDays": 1,
                  "handoffLocalTime": "09:00:00",
                  "effectiveFrom": "2026-06-01",
                  "participantUserIds": ["%s", "%s"]
                }
                """,
                member.getId(), admin.getId());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/on-call-schedules")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/on-call-schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schedule.name").value("Primary"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].position").value(0))
                .andReturn();

        String scheduleId = extractJsonString(created, "id");

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/on-call-schedules")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/on-call-schedules/" + scheduleId + "/coverage")
                        .header("Authorization", "Bearer " + memberToken)
                        .param("at", "2026-06-01T08:59:59Z")
                        .param("upcoming", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").isEmpty())
                .andExpect(jsonPath("$.upcoming.length()").value(1));

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/on-call-schedules/" + scheduleId + "/coverage")
                        .header("Authorization", "Bearer " + memberToken)
                        .param("at", "2026-06-01T09:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.userId").value(member.getId().toString()));

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/on-call-schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody.replace("Primary", "primary")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_NAME"));

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/on-call-schedules/" + scheduleId + "/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/on-call-schedules")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/on-call-schedules")
                        .header("Authorization", "Bearer " + memberToken)
                        .param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/on-call-schedules/" + scheduleId + "/coverage")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCHEDULE_NOT_ACTIVE"));

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/on-call-schedules/" + scheduleId + "/reactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/organizations/" + orgId + "/on-call-schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        String otherOrgId = createOrganization(outsiderToken, "Other", "other-oncall");
        mockMvc.perform(get("/api/v1/organizations/" + otherOrgId + "/on-call-schedules/" + scheduleId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void weeklyScheduleRequiresHandoffDayInServiceLayer() throws Exception {
        User owner = createUser("weekly@example.com");
        String token = login("weekly@example.com");
        String orgId = createOrganization(token, "Weekly Org", "weekly-org");

        String body = String.format(
                """
                {
                  "name": "Weekly",
                  "timezone": "UTC",
                  "rotationLengthDays": 7,
                  "handoffLocalTime": "10:00:00",
                  "effectiveFrom": "2026-01-05",
                  "participantUserIds": ["%s"]
                }
                """,
                owner.getId());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/on-call-schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
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
}
