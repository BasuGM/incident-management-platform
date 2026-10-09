package com.example.incidentmanagement.incident;

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
class IncidentPostmortemControllerIntegrationTest extends IntegrationTestBase {

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
    void unauthenticatedRequestsReturn401() throws Exception {
        OrgFixture f = newOrgFixture("unauth");
        String incidentId = createIncident(f.memberToken, f.orgId, "I", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"summary\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path + "/publish")).andExpect(status().isUnauthorized());
    }

    @Test
    void createAndGetSingleton() throws Exception {
        OrgFixture f = newOrgFixture("create-get");
        String incidentId = createIncident(f.memberToken, f.orgId, "Outage", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.authorId").value(f.member.getId().toString()))
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.organizationId").value(f.orgId))
                .andExpect(jsonPath("$.title").value("Postmortem: Outage"));

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void missingSingletonReturns404() throws Exception {
        OrgFixture f = newOrgFixture("missing");
        String incidentId = createIncident(f.memberToken, f.orgId, "None", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);

        mockMvc.perform(get(postmortemPath(f.orgId, incidentId))
                        .header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("POSTMORTEM_NOT_FOUND"));
    }

    @Test
    void viewerCannotCreateMemberCan() throws Exception {
        OrgFixture f = newOrgFixture("viewer-create");
        String incidentId = createIncident(f.memberToken, f.orgId, "RBAC", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());
    }

    @Test
    void createRejectedWhenIncidentNotResolved() throws Exception {
        OrgFixture f = newOrgFixture("open");
        String incidentId = createIncident(f.memberToken, f.orgId, "Open", "SEV2");
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM"));
    }

    @Test
    void updateDraftPublishUnpublishAndDelete() throws Exception {
        OrgFixture f = newOrgFixture("lifecycle");
        String incidentId = createIncident(f.memberToken, f.orgId, "Life", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());

        mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"What happened\",\"rootCause\":\"Bug\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("What happened"));

        mockMvc.perform(post(path + "/publish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.publishedById").value(f.member.getId().toString()));

        mockMvc.perform(post(path + "/unpublish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());

        mockMvc.perform(delete(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void publishValidationFailureReturns409() throws Exception {
        OrgFixture f = newOrgFixture("pub-val");
        String incidentId = createIncident(f.memberToken, f.orgId, "Val", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());

        mockMvc.perform(post(path + "/publish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("POSTMORTEM_PUBLISH_VALIDATION_FAILED"));
    }

    @Test
    void archiveAndUnarchiveAdminOnly() throws Exception {
        OrgFixture f = newOrgFixture("archive");
        String incidentId = createIncident(f.memberToken, f.orgId, "Arch", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());
        mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"S\",\"rootCause\":\"R\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(path + "/publish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(path + "/archive").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path + "/archive").header("Authorization", "Bearer " + f.adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").exists());

        mockMvc.perform(post(path + "/unarchive").header("Authorization", "Bearer " + f.adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void cannotPatchPublishedPostmortem() throws Exception {
        OrgFixture f = newOrgFixture("no-patch-pub");
        String incidentId = createIncident(f.memberToken, f.orgId, "Pub", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());
        mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"S\",\"rootCause\":\"R\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(path + "/publish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Changed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("POSTMORTEM_NOT_EDITABLE"));
    }

    @Test
    void organizationListDefaultsToPublishedAndSupportsFilters() throws Exception {
        OrgFixture f = newOrgFixture("org-list");
        String incident1 = createIncident(f.memberToken, f.orgId, "One", "SEV2");
        String incident2 = createIncident(f.memberToken, f.orgId, "Two", "SEV3");
        resolveIncident(f.memberToken, f.orgId, incident1);
        resolveIncident(f.memberToken, f.orgId, incident2);

        String path1 = postmortemPath(f.orgId, incident1);
        String path2 = postmortemPath(f.orgId, incident2);
        mockMvc.perform(post(path1).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());
        mockMvc.perform(post(path2).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());
        mockMvc.perform(patch(path2)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Pub\",\"rootCause\":\"R\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(path2 + "/publish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isOk());

        String listPath = "/api/v1/organizations/" + f.orgId + "/postmortems";
        mockMvc.perform(get(listPath).header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"));

        mockMvc.perform(get(listPath + "?status=DRAFT").header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));

        mockMvc.perform(get(listPath + "?status=ALL").header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void organizationListPaginationAndMaxSize() throws Exception {
        OrgFixture f = newOrgFixture("page");
        for (int i = 0; i < 3; i++) {
            String incidentId = createIncident(f.memberToken, f.orgId, "P" + i, "SEV2");
            resolveIncident(f.memberToken, f.orgId, incidentId);
            String path = postmortemPath(f.orgId, incidentId);
            mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                    .andExpect(status().isCreated());
            mockMvc.perform(patch(path)
                            .header("Authorization", "Bearer " + f.memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"summary\":\"S\",\"rootCause\":\"R\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(post(path + "/publish").header("Authorization", "Bearer " + f.memberToken))
                    .andExpect(status().isOk());
        }

        String listPath = "/api/v1/organizations/" + f.orgId + "/postmortems";
        mockMvc.perform(get(listPath + "?page=0&size=2").header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get(listPath + "?size=500").header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void tenantIsolationOnRead() throws Exception {
        OrgFixture fA = newOrgFixture("iso-a");
        OrgFixture fB = newOrgFixture("iso-b");
        String incidentId = createIncident(fA.memberToken, fA.orgId, "A", "SEV2");
        resolveIncident(fA.memberToken, fA.orgId, incidentId);
        mockMvc.perform(post(postmortemPath(fA.orgId, incidentId))
                        .header("Authorization", "Bearer " + fA.memberToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get(postmortemPath(fB.orgId, incidentId))
                        .header("Authorization", "Bearer " + fB.memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidStatusFilterReturnsValidationError() throws Exception {
        OrgFixture f = newOrgFixture("bad-filter");
        String listPath = "/api/v1/organizations/" + f.orgId + "/postmortems";

        mockMvc.perform(get(listPath + "?status=NOT_A_STATUS")
                        .header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void patchExplicitNullClearsSummary() throws Exception {
        OrgFixture f = newOrgFixture("patch-null");
        String incidentId = createIncident(f.memberToken, f.orgId, "Null", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());
        mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Filled in\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Filled in"));

        mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(""));
    }

    @Test
    void publishRejectsWhitespaceOnlySummary() throws Exception {
        OrgFixture f = newOrgFixture("ws-pub");
        String incidentId = createIncident(f.memberToken, f.orgId, "Ws", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());
        mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"   \",\"rootCause\":\"Real cause\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(path + "/publish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("POSTMORTEM_PUBLISH_VALIDATION_FAILED"));
    }

    @Test
    void deletePublishedPostmortemReturns409() throws Exception {
        OrgFixture f = newOrgFixture("del-pub");
        String incidentId = createIncident(f.memberToken, f.orgId, "NoDel", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());
        mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"S\",\"rootCause\":\"R\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(path + "/publish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("POSTMORTEM_NOT_EDITABLE"));
    }

    @Test
    void memberCannotDeleteAnotherAuthorsDraft() throws Exception {
        OrgFixture f = newOrgFixture("del-other");
        User peer = createUser("del-other-peer@example.com");
        addMember(f.ownerToken, f.orgId, peer.getId(), "MEMBER");
        String peerToken = login(peer.getEmail());

        String incidentId = createIncident(f.memberToken, f.orgId, "Other", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(path).header("Authorization", "Bearer " + peerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationListRejectsNonMember() throws Exception {
        OrgFixture f = newOrgFixture("list-forbidden");
        String listPath = "/api/v1/organizations/" + f.orgId + "/postmortems";

        mockMvc.perform(get(listPath).header("Authorization", "Bearer " + f.outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishArchivedPostmortemReturnsInvalidTransition() throws Exception {
        OrgFixture f = newOrgFixture("pub-arch");
        String incidentId = createIncident(f.memberToken, f.orgId, "ArchPub", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated());
        mockMvc.perform(patch(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"S\",\"rootCause\":\"R\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(path + "/publish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isOk());
        mockMvc.perform(post(path + "/archive").header("Authorization", "Bearer " + f.adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(path + "/publish").header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_POSTMORTEM_STATUS_TRANSITION"));
    }

    @Test
    void responseIncludesAuthorFieldsWithoutSerializationFailure() throws Exception {
        OrgFixture f = newOrgFixture("serial");
        f.member.setFirstName("Post");
        f.member.setLastName("Author");
        userRepository.save(f.member);

        String incidentId = createIncident(f.memberToken, f.orgId, "Ser", "SEV2");
        resolveIncident(f.memberToken, f.orgId, incidentId);
        String path = postmortemPath(f.orgId, incidentId);

        mockMvc.perform(post(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorEmail").value(f.member.getEmail()))
                .andExpect(jsonPath("$.authorFirstName").value("Post"))
                .andExpect(jsonPath("$.authorLastName").value("Author"))
                .andExpect(jsonPath("$.author").doesNotExist());
    }

    private OrgFixture newOrgFixture(String slugPrefix) throws Exception {
        User owner = createUser(slugPrefix + "-owner@example.com");
        User admin = createUser(slugPrefix + "-admin@example.com");
        User member = createUser(slugPrefix + "-member@example.com");
        User viewer = createUser(slugPrefix + "-viewer@example.com");
        User outsider = createUser(slugPrefix + "-outsider@example.com");

        String ownerToken = login(owner.getEmail());
        String adminToken = login(admin.getEmail());
        String memberToken = login(member.getEmail());
        String viewerToken = login(viewer.getEmail());
        String outsiderToken = login(outsider.getEmail());

        String orgId = createOrganization(ownerToken, "Org " + slugPrefix, slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        addMember(ownerToken, orgId, admin.getId(), "ADMIN");
        addMember(ownerToken, orgId, member.getId(), "MEMBER");
        addMember(ownerToken, orgId, viewer.getId(), "VIEWER");

        return new OrgFixture(owner, admin, member, viewer, outsider, orgId, ownerToken, adminToken, memberToken, viewerToken, outsiderToken);
    }

    private static String postmortemPath(String orgId, String incidentId) {
        return "/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/postmortem";
    }

    private void resolveIncident(String token, String orgId, String incidentId) throws Exception {
        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
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

    private record OrgFixture(
            User owner,
            User admin,
            User member,
            User viewer,
            User outsider,
            String orgId,
            String ownerToken,
            String adminToken,
            String memberToken,
            String viewerToken,
            String outsiderToken) {}
}
