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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
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
class IncidentCommentControllerIntegrationTest extends IntegrationTestBase {

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
        User owner = createUser("cmt-unauth@example.com");
        String ownerToken = login("cmt-unauth@example.com");
        String orgId = createOrganization(ownerToken, "Unauth", "unauth-" + UUID.randomUUID().toString().substring(0, 8));
        String incidentId = createIncident(ownerToken, orgId, "I", "SEV2");
        String path = commentsPath(orgId, incidentId);

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(path + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(path + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void readRbacAllMembersAndNonMemberForbidden() throws Exception {
        OrgFixture f = newOrgFixture("read-rbac");
        String incidentId = createIncident(f.memberToken, f.orgId, "Read", "SEV2");
        String path = commentsPath(f.orgId, incidentId);

        mockMvc.perform(postCommentRequest(f.memberToken, path, "Hello")).andExpect(status().isCreated());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRbacAndAuthorFromPrincipal() throws Exception {
        OrgFixture f = newOrgFixture("create-rbac");
        String incidentId = createIncident(f.memberToken, f.orgId, "Create", "SEV2");
        String path = commentsPath(f.orgId, incidentId);

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"nope\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(postCommentRequest(f.ownerToken, path, "Owner says"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(f.owner.getId().toString()));

        mockMvc.perform(postCommentRequest(f.adminToken, path, "Admin says"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"body\":\"Member note\",\"authorId\":\"%s\"}", f.owner.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(f.member.getId().toString()))
                .andExpect(jsonPath("$.body").value("Member note"))
                .andExpect(jsonPath("$.author").doesNotExist())
                .andExpect(jsonPath("$.incident").doesNotExist());
    }

    @Test
    void updateRbacAuthorOnly() throws Exception {
        OrgFixture f = newOrgFixture("update-rbac");
        String incidentId = createIncident(f.memberToken, f.orgId, "Update", "SEV2");
        String path = commentsPath(f.orgId, incidentId);

        MvcResult created = mockMvc.perform(postCommentRequest(f.memberToken, path, "Original"))
                .andExpect(status().isCreated())
                .andReturn();
        String commentId = extractJsonString(created, "id");
        String createdAt = extractJsonString(created, "createdAt");

        mockMvc.perform(patch(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Edited"))
                .andExpect(jsonPath("$.authorId").value(f.member.getId().toString()))
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.organizationId").value(f.orgId))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        mockMvc.perform(patch(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hijack\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hijack\"}"))
                .andExpect(status().isForbidden());

        User otherMember = createUser("other-member@example.com");
        addMember(f.ownerToken, f.orgId, otherMember.getId(), "MEMBER");
        String otherToken = login("other-member@example.com");
        mockMvc.perform(patch(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hijack\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hijack\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRbacAndSoftDeleteTombstone() throws Exception {
        OrgFixture f = newOrgFixture("delete-rbac");
        String incidentId = createIncident(f.memberToken, f.orgId, "Delete", "SEV2");
        String path = commentsPath(f.orgId, incidentId);

        MvcResult created = mockMvc.perform(postCommentRequest(f.memberToken, path, "To delete"))
                .andExpect(status().isCreated())
                .andReturn();
        String commentId = extractJsonString(created, "id");

        User otherMember = createUser("del-other@example.com");
        addMember(f.ownerToken, f.orgId, otherMember.getId(), "MEMBER");
        String otherToken = login("del-other@example.com");
        mockMvc.perform(delete(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].body").value(""))
                .andExpect(jsonPath("$.content[0].deleted").value(true))
                .andExpect(jsonPath("$.content[0].deletedAt").isNotEmpty());

        String modId = extractJsonString(
                mockMvc.perform(postCommentRequest(f.memberToken, path, "Moderate me"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "id");

        mockMvc.perform(delete(commentItemPath(f.orgId, incidentId, modId))
                        .header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(status().isNoContent());

        String adminTargetId = extractJsonString(
                mockMvc.perform(postCommentRequest(f.memberToken, path, "Admin mod"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "id");
        mockMvc.perform(delete(commentItemPath(f.orgId, incidentId, adminTargetId))
                        .header("Authorization", "Bearer " + f.adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void terminalIncidentWritesRejectedReadsAllowed() throws Exception {
        OrgFixture f = newOrgFixture("terminal");
        String incidentId = createIncident(f.memberToken, f.orgId, "Terminal", "SEV2");
        String path = commentsPath(f.orgId, incidentId);

        String commentId = extractJsonString(
                mockMvc.perform(postCommentRequest(f.memberToken, path, "Before close"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "id");

        resolveIncident(f.ownerToken, f.orgId, incidentId);

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Late\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Edit late\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isConflict());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].body").value("Before close"));

        String cancelledId = createIncident(f.memberToken, f.orgId, "Cancel", "SEV3");
        cancelIncident(f.adminToken, f.orgId, cancelledId);
        String cancelPath = commentsPath(f.orgId, cancelledId);
        mockMvc.perform(post(cancelPath)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"nope\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void tenantIsolation() throws Exception {
        User userA = createUser("cmt-a@example.com");
        User userB = createUser("cmt-b@example.com");
        String tokenA = login("cmt-a@example.com");
        String tokenB = login("cmt-b@example.com");

        String orgA = createOrganization(tokenA, "Org A", "cmt-a-" + UUID.randomUUID().toString().substring(0, 8));
        String orgB = createOrganization(tokenB, "Org B", "cmt-b-" + UUID.randomUUID().toString().substring(0, 8));

        String incidentA = createIncident(tokenA, orgA, "A", "SEV2");
        String incidentB = createIncident(tokenB, orgB, "B", "SEV2");

        String commentBId = extractJsonString(
                mockMvc.perform(postCommentRequest(tokenB, commentsPath(orgB, incidentB), "Secret B"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "id");

        mockMvc.perform(get(commentsPath(orgA, incidentB)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(commentsPath(orgB, incidentA)).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(commentsPath(orgB, incidentB))
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"cross post\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(commentItemPath(orgB, incidentB, commentBId))
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hack\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(commentItemPath(orgB, incidentB, commentBId))
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(commentsPath(orgA, incidentA)).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void incidentScoping() throws Exception {
        OrgFixture f = newOrgFixture("scope");
        String incidentA = createIncident(f.memberToken, f.orgId, "Inc A", "SEV2");
        String incidentB = createIncident(f.memberToken, f.orgId, "Inc B", "SEV3");

        mockMvc.perform(postCommentRequest(f.memberToken, commentsPath(f.orgId, incidentA), "Only A"))
                .andExpect(status().isCreated());
        mockMvc.perform(postCommentRequest(f.memberToken, commentsPath(f.orgId, incidentB), "Only B"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(commentsPath(f.orgId, incidentA)).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].body").value("Only A"))
                .andExpect(jsonPath("$.content[0].incidentId").value(incidentA));

        String commentOnA = extractJsonString(
                mockMvc.perform(postCommentRequest(f.memberToken, commentsPath(f.orgId, incidentA), "Patch target"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "id");

        mockMvc.perform(patch(commentItemPath(f.orgId, incidentB, commentOnA))
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Wrong incident\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(commentsPath(f.orgId, incidentA)).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(jsonPath("$.content[1].body").value("Patch target"));

        mockMvc.perform(delete(commentItemPath(f.orgId, incidentB, commentOnA))
                        .header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginationOrderingAndSizeCap() throws Exception {
        OrgFixture f = newOrgFixture("page");
        String incidentId = createIncident(f.ownerToken, f.orgId, "Page", "SEV4");
        String path = commentsPath(f.orgId, incidentId);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(postCommentRequest(f.ownerToken, path, "Comment " + i))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(5));

        MvcResult page0 = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + f.ownerToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].body").value("Comment 0"))
                .andExpect(jsonPath("$.content[1].body").value("Comment 1"))
                .andReturn();

        MvcResult page1 = mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + f.ownerToken)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].body").value("Comment 2"))
                .andExpect(jsonPath("$.content[1].body").value("Comment 3"))
                .andReturn();

        Set<String> ids = new HashSet<>();
        ids.addAll(extractJsonPathArray(page0, "id"));
        ids.addAll(extractJsonPathArray(page1, "id"));
        Assertions.assertThat(ids).hasSize(4);

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.ownerToken).param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void validationAndTrimming() throws Exception {
        OrgFixture f = newOrgFixture("valid");
        String incidentId = createIncident(f.memberToken, f.orgId, "Valid", "SEV2");
        String path = commentsPath(f.orgId, incidentId);
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":null}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest());

        String tooLong = "x".repeat(5001);
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"body\":\"%s\"}", tooLong)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"  trimmed  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("trimmed"));

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"line1\\nline2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("line1\nline2"));

        String commentId = extractJsonString(
                mockMvc.perform(postCommentRequest(f.memberToken, path, "Patch me"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "id");
        String patchPath = commentItemPath(f.orgId, incidentId, commentId);

        mockMvc.perform(patch(patchPath)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(patchPath)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletedCommentCannotBeEditedOrDeletedAgain() throws Exception {
        OrgFixture f = newOrgFixture("tombstone");
        String incidentId = createIncident(f.memberToken, f.orgId, "Tomb", "SEV2");
        String path = commentsPath(f.orgId, incidentId);

        String commentId = extractJsonString(
                mockMvc.perform(postCommentRequest(f.memberToken, path, "Sensitive text"))
                        .andExpect(status().isCreated())
                        .andReturn(),
                "id");

        mockMvc.perform(delete(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.memberToken))
                .andExpect(jsonPath("$.content[0].body").value(""))
                .andExpect(jsonPath("$.content[0].deleted").value(true));

        mockMvc.perform(patch(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Restore\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete(commentItemPath(f.orgId, incidentId, commentId))
                        .header("Authorization", "Bearer " + f.memberToken))
                .andExpect(status().isConflict());
    }

    @Test
    void listResponseIncludesAuthorFieldsWithoutSerializationFailure() throws Exception {
        OrgFixture f = newOrgFixture("list-author");
        f.member.setFirstName("List");
        f.member.setLastName("Author");
        userRepository.save(f.member);

        String incidentId = createIncident(f.memberToken, f.orgId, "List", "SEV2");
        String path = commentsPath(f.orgId, incidentId);

        mockMvc.perform(postCommentRequest(f.memberToken, path, "Thread entry"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(path).header("Authorization", "Bearer " + f.viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].authorId").value(f.member.getId().toString()))
                .andExpect(jsonPath("$.content[0].authorEmail").value(f.member.getEmail()))
                .andExpect(jsonPath("$.content[0].authorFirstName").value("List"))
                .andExpect(jsonPath("$.content[0].authorLastName").value("Author"));
    }

    @Test
    void createAcceptsHtmlLikePlainTextUnmodified() throws Exception {
        OrgFixture f = newOrgFixture("html-plain");
        String incidentId = createIncident(f.memberToken, f.orgId, "Plain", "SEV2");
        String path = commentsPath(f.orgId, incidentId);
        String payload = "<b>not bold</b>";

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"body\":\"%s\"}", payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(payload));
    }

    @Test
    void authorFieldsInResponse() throws Exception {
        OrgFixture f = newOrgFixture("author");
        f.member.setFirstName("Ada");
        f.member.setLastName("Lovelace");
        userRepository.save(f.member);

        String incidentId = createIncident(f.memberToken, f.orgId, "Author", "SEV2");
        mockMvc.perform(post(commentsPath(f.orgId, incidentId))
                        .header("Authorization", "Bearer " + f.memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Note\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(f.member.getId().toString()))
                .andExpect(jsonPath("$.authorEmail").value(f.member.getEmail()))
                .andExpect(jsonPath("$.authorFirstName").value("Ada"))
                .andExpect(jsonPath("$.authorLastName").value("Lovelace"));
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

    private static org.springframework.test.web.servlet.RequestBuilder postCommentRequest(
            String token, String path, String body) {
        return post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"body\":\"%s\"}", body.replace("\"", "\\\"").replace("\n", "\\n")));
    }

    private void resolveIncident(String token, String orgId, String incidentId) throws Exception {
        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
    }

    private void cancelIncident(String token, String orgId, String incidentId) throws Exception {
        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());
    }

    private static String commentsPath(String orgId, String incidentId) {
        return "/api/v1/organizations/" + orgId + "/incidents/" + incidentId + "/comments";
    }

    private static String commentItemPath(String orgId, String incidentId, String commentId) {
        return commentsPath(orgId, incidentId) + "/" + commentId;
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
