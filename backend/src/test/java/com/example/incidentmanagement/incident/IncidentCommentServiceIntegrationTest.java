package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationMemberRepository;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class IncidentCommentServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentCommentService incidentCommentService;

    @Autowired
    private IncidentCommentRepository incidentCommentRepository;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanAll();
    }

    @Test
    void memberCreatesCommentAnotherMemberReadsAuthorEditsAndDeletes() {
        var ctx = new OrgContext("collab");
        Incident incident = createOpenIncident(ctx, "API down");

        IncidentComment created = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), "Initial note");
        assertThat(created.getAuthor().getId()).isEqualTo(ctx.member.getId());
        assertThat(created.getDeletedAt()).isNull();

        IncidentComment readByOther = incidentCommentService.getComment(
                ctx.organization.getId(), incident.getId(), created.getId(), ctx.admin.getId());
        assertThat(readByOther.getBody()).isEqualTo("Initial note");

        IncidentComment updated = incidentCommentService.updateComment(
                ctx.organization.getId(),
                incident.getId(),
                created.getId(),
                ctx.member.getId(),
                "Updated note");
        assertThat(updated.getBody()).isEqualTo("Updated note");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());

        IncidentComment deleted = incidentCommentService.deleteComment(
                ctx.organization.getId(), incident.getId(), created.getId(), ctx.member.getId());
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getBody()).isEmpty();

        IncidentComment reloaded = incidentCommentRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.isDeleted()).isTrue();
        assertThat(reloaded.getBody()).isEmpty();
    }

    @Test
    void ownerModeratesDeleteOfAnotherMembersComment() {
        var ctx = new OrgContext("moderate");
        Incident incident = createOpenIncident(ctx, "Spam");
        IncidentComment comment = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), "Inappropriate");

        incidentCommentService.deleteComment(
                ctx.organization.getId(), incident.getId(), comment.getId(), ctx.owner.getId());

        assertThat(incidentCommentRepository.findById(comment.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void memberCannotDeleteAnotherMembersComment() {
        var ctx = new OrgContext("no-mod");
        Incident incident = createOpenIncident(ctx, "Thread");
        User otherMember = IncidentTestFixtures.createUser(
                userRepository, passwordEncoder, "other-member@example.com");
        addMember(ctx.organization, otherMember, OrganizationRole.MEMBER);

        IncidentComment comment = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), "Mine");

        assertThatThrownBy(() -> incidentCommentService.deleteComment(
                        ctx.organization.getId(), incident.getId(), comment.getId(), otherMember.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void viewerCannotCreateOrDelete() {
        var ctx = new OrgContext("viewer");
        Incident incident = createOpenIncident(ctx, "Read only");

        assertThatThrownBy(() -> incidentCommentService.createComment(
                        ctx.organization.getId(), incident.getId(), ctx.viewer.getId(), "Nope"))
                .isInstanceOf(ForbiddenException.class);

        IncidentComment comment = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), "Visible");

        assertThatThrownBy(() -> incidentCommentService.deleteComment(
                        ctx.organization.getId(), incident.getId(), comment.getId(), ctx.viewer.getId()))
                .isInstanceOf(ForbiddenException.class);

        assertThat(incidentCommentService.getComment(
                        ctx.organization.getId(), incident.getId(), comment.getId(), ctx.viewer.getId())
                .getBody())
                .isEqualTo("Visible");
    }

    @Test
    void terminalIncidentRejectsWritesButAllowsReads() {
        var ctx = new OrgContext("terminal");
        Incident incident = createOpenIncident(ctx, "Closed");
        IncidentComment existing = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), "Before close");

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.RESOLVED).build());

        assertThatThrownBy(() -> incidentCommentService.createComment(
                        ctx.organization.getId(), incident.getId(), ctx.member.getId(), "Late"))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo("INCIDENT_NOT_EDITABLE");

        assertThatThrownBy(() -> incidentCommentService.updateComment(
                        ctx.organization.getId(),
                        incident.getId(),
                        existing.getId(),
                        ctx.member.getId(),
                        "Edit"))
                .isInstanceOf(ConflictException.class);

        assertThat(incidentCommentService.listComments(
                        ctx.organization.getId(), incident.getId(), ctx.viewer.getId(), PageRequest.of(0, 10))
                .getContent())
                .extracting(IncidentComment::getId)
                .contains(existing.getId());
    }

    @Test
    void chronologicalListingThroughService() {
        var ctx = new OrgContext("order");
        Incident incident = createOpenIncident(ctx, "Ordering");

        IncidentComment first = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), "First");
        IncidentComment second = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), "Second");

        List<IncidentComment> content = incidentCommentService
                .listComments(ctx.organization.getId(), incident.getId(), ctx.viewer.getId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(content).extracting(IncidentComment::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void crossOrganizationAccessForbidden() {
        var ctxA = new OrgContext("iso-a");
        var ctxB = new OrgContext("iso-b");
        Incident incidentA = createOpenIncident(ctxA, "A");
        IncidentComment comment = incidentCommentService.createComment(
                ctxA.organization.getId(), incidentA.getId(), ctxA.member.getId(), "Secret");

        assertThatThrownBy(() -> incidentCommentService.getComment(
                        ctxB.organization.getId(), incidentA.getId(), comment.getId(), ctxB.member.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void globalAdminUserRoleDoesNotOverrideOrganizationMemberRole() {
        var ctx = new OrgContext("global-admin");
        User globalAdminUser = IncidentTestFixtures.createUser(
                userRepository, passwordEncoder, "global-admin@example.com");
        globalAdminUser.setRole(com.example.incidentmanagement.user.UserRole.ADMIN);
        userRepository.save(globalAdminUser);
        addMember(ctx.organization, globalAdminUser, OrganizationRole.MEMBER);

        Incident incident = createOpenIncident(ctx, "Roles");
        IncidentComment comment = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), "Member wrote this");

        assertThatThrownBy(() -> incidentCommentService.deleteComment(
                        ctx.organization.getId(), incident.getId(), comment.getId(), globalAdminUser.getId()))
                .isInstanceOf(ForbiddenException.class);

        IncidentComment own = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), globalAdminUser.getId(), "My comment");
        assertThat(own.getAuthor().getId()).isEqualTo(globalAdminUser.getId());
    }

    @Test
    void serviceStoresHtmlLikeBodyAsPlainText() {
        var ctx = new OrgContext("plain");
        Incident incident = createOpenIncident(ctx, "XSS");
        String payload = "<script>alert(1)</script>";

        IncidentComment created = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), payload);

        assertThat(created.getBody()).isEqualTo(payload);
        assertThat(incidentCommentService.getComment(
                        ctx.organization.getId(), incident.getId(), created.getId(), ctx.viewer.getId())
                .getBody())
                .isEqualTo(payload);
    }

    @Test
    void softDeletedCommentRemainsInListAsTombstone() {
        var ctx = new OrgContext("tombstone");
        Incident incident = createOpenIncident(ctx, "Thread");
        IncidentComment comment = incidentCommentService.createComment(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), "Remove me");

        incidentCommentService.deleteComment(
                ctx.organization.getId(), incident.getId(), comment.getId(), ctx.member.getId());

        List<IncidentComment> listed = incidentCommentService
                .listComments(ctx.organization.getId(), incident.getId(), ctx.viewer.getId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst().isDeleted()).isTrue();
        assertThat(listed.getFirst().getBody()).isEmpty();
    }

    private Incident createOpenIncident(OrgContext ctx, String title) {
        return incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams(title, null, IncidentSeverity.SEV3, null, null));
    }

    private void addMember(Organization organization, User user, OrganizationRole role) {
        OrganizationMember organizationMember = new OrganizationMember();
        organizationMember.setOrganization(organization);
        organizationMember.setUser(user);
        organizationMember.setRole(role);
        organizationMemberRepository.save(organizationMember);
    }

    private final class OrgContext {
        final Organization organization;
        final User owner;
        final User member;
        final User admin;
        final User viewer;

        OrgContext(String slugPrefix) {
            owner = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-owner@example.com");
            member = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-member@example.com");
            admin = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-admin@example.com");
            viewer = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-viewer@example.com");
            organization = IncidentTestFixtures.createOrganization(
                    organizationRepository, IncidentTestFixtures.uniqueSlug(slugPrefix));
            addMember(organization, owner, OrganizationRole.OWNER);
            addMember(organization, member, OrganizationRole.MEMBER);
            addMember(organization, admin, OrganizationRole.ADMIN);
            addMember(organization, viewer, OrganizationRole.VIEWER);
        }
    }
}
