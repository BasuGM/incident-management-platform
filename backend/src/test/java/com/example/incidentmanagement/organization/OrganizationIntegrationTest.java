package com.example.incidentmanagement.organization;

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
class OrganizationIntegrationTest extends IntegrationTestBase {

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
    void organizationIsolationAndRoles() throws Exception {
        User userA = createUser("usera@example.com");
        User userB = createUser("userb@example.com");
        String tokenA = login("usera@example.com");
        String tokenB = login("userb@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"slug\":\"acme\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentUserRole").value("OWNER"))
                .andReturn();
        String orgId = extractJsonString(createResult, "id");

        mockMvc.perform(get("/api/v1/organizations").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("acme"));

        mockMvc.perform(get("/api/v1/organizations/" + orgId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/teams")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Payments\",\"description\":\"Pay\"}"))
                .andExpect(status().isForbidden());

        MvcResult teamResult = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/teams")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Payments\",\"description\":\"Pay\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String teamId = extractJsonString(teamResult, "id");

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":\"%s\",\"role\":\"VIEWER\"}", userB.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"userId\":\"%s\"}", userB.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"userId\":\"%s\"}", UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationRoleEnforcement() throws Exception {
        User owner = createUser("owner@example.com");
        User viewer = createUser("viewer@example.com");
        User member = createUser("member@example.com");
        String ownerToken = login("owner@example.com");
        String viewerToken = login("viewer@example.com");
        String memberToken = login("member@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Role Org\",\"slug\":\"role-org\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String orgId = extractJsonString(createResult, "id");

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":\"%s\",\"role\":\"VIEWER\"}", viewer.getId())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":\"%s\",\"role\":\"MEMBER\"}", member.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/organizations/" + orgId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"userId\":\"%s\",\"role\":\"VIEWER\"}", member.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/teams")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneak\",\"description\":\"\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Platform\",\"description\":\"Core\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/organizations/" + orgId + "/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Platform\",\"description\":\"Dup\"}"))
                .andExpect(status().isConflict());
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
