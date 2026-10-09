package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.common.exception.PostmortemNotFoundException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationMemberRepository;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class IncidentPostmortemServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentPostmortemService incidentPostmortemService;

    @Autowired
    private IncidentPostmortemRepository incidentPostmortemRepository;

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
    void createRetrievePublishUnpublishAndDeleteDraft() {
        var ctx = new OrgContext("flow");
        Incident incident = createResolvedIncident(ctx, "Payment outage");

        IncidentPostmortem created = incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), CreatePostmortemParams.empty());
        assertThat(created.getStatus()).isEqualTo(IncidentPostmortemStatus.DRAFT);
        assertThat(created.getTitle()).isEqualTo("Postmortem: Payment outage");

        IncidentPostmortem read = incidentPostmortemService.getPostmortemByIncident(
                ctx.organization.getId(), incident.getId(), ctx.viewer.getId());
        assertThat(read.getId()).isEqualTo(created.getId());

        incidentPostmortemService.updateDraft(
                ctx.organization.getId(),
                incident.getId(),
                created.getId(),
                ctx.member.getId(),
                PostmortemDraftUpdate.builder()
                        .summary("Customers could not pay")
                        .rootCause("Bad deploy")
                        .build());

        IncidentPostmortem published = incidentPostmortemService.publishPostmortem(
                ctx.organization.getId(), incident.getId(), created.getId(), ctx.member.getId());
        assertThat(published.getStatus()).isEqualTo(IncidentPostmortemStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPublishedBy().getId()).isEqualTo(ctx.member.getId());

        IncidentPostmortem unpublished = incidentPostmortemService.unpublishPostmortem(
                ctx.organization.getId(), incident.getId(), created.getId(), ctx.member.getId());
        assertThat(unpublished.getStatus()).isEqualTo(IncidentPostmortemStatus.DRAFT);
        assertThat(unpublished.getPublishedAt()).isNull();

        incidentPostmortemService.deleteDraftPostmortem(
                ctx.organization.getId(), incident.getId(), created.getId(), ctx.member.getId());
        assertThat(incidentPostmortemRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void rejectsCreateOnOpenIncident() {
        var ctx = new OrgContext("open");
        Incident incident = createOpenIncident(ctx, "Still open");

        assertThatThrownBy(() -> incidentPostmortemService.createPostmortem(
                        ctx.organization.getId(),
                        incident.getId(),
                        ctx.member.getId(),
                        CreatePostmortemParams.empty()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo("INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM");
    }

    @Test
    void rejectsDuplicateCreate() {
        var ctx = new OrgContext("dup");
        Incident incident = createResolvedIncident(ctx, "Once");

        incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), CreatePostmortemParams.empty());

        assertThatThrownBy(() -> incidentPostmortemService.createPostmortem(
                        ctx.organization.getId(),
                        incident.getId(),
                        ctx.member.getId(),
                        CreatePostmortemParams.empty()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo("POSTMORTEM_ALREADY_EXISTS");
    }

    @Test
    void tenantIsolationOnRead() {
        var ctxA = new OrgContext("iso-a");
        var ctxB = new OrgContext("iso-b");
        Incident incidentA = createResolvedIncident(ctxA, "A");
        IncidentPostmortem postmortem = incidentPostmortemService.createPostmortem(
                ctxA.organization.getId(), incidentA.getId(), ctxA.member.getId(), CreatePostmortemParams.empty());

        assertThatThrownBy(() -> incidentPostmortemService.getPostmortem(
                        ctxB.organization.getId(),
                        incidentA.getId(),
                        postmortem.getId(),
                        ctxB.member.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminPublishesAnotherMembersDraft() {
        var ctx = new OrgContext("admin-pub");
        Incident incident = createResolvedIncident(ctx, "Shared");
        IncidentPostmortem draft = incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), CreatePostmortemParams.empty());
        incidentPostmortemService.updateDraft(
                ctx.organization.getId(),
                incident.getId(),
                draft.getId(),
                ctx.member.getId(),
                PostmortemDraftUpdate.builder().summary("Summary").rootCause("Cause").build());

        IncidentPostmortem published = incidentPostmortemService.publishPostmortem(
                ctx.organization.getId(), incident.getId(), draft.getId(), ctx.admin.getId());

        assertThat(published.getStatus()).isEqualTo(IncidentPostmortemStatus.PUBLISHED);
        assertThat(published.getPublishedBy().getId()).isEqualTo(ctx.admin.getId());
    }

    @Test
    void archivePreservesPublicationMetadataAndUnarchiveReturnsToDraft() {
        var ctx = new OrgContext("archive");
        Incident incident = createResolvedIncident(ctx, "Archive me");
        IncidentPostmortem draft = incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), CreatePostmortemParams.empty());
        incidentPostmortemService.updateDraft(
                ctx.organization.getId(),
                incident.getId(),
                draft.getId(),
                ctx.member.getId(),
                PostmortemDraftUpdate.builder().summary("S").rootCause("R").build());
        IncidentPostmortem published = incidentPostmortemService.publishPostmortem(
                ctx.organization.getId(), incident.getId(), draft.getId(), ctx.member.getId());

        IncidentPostmortem archived = incidentPostmortemService.archivePostmortem(
                ctx.organization.getId(), incident.getId(), draft.getId(), ctx.admin.getId());
        assertThat(archived.getStatus()).isEqualTo(IncidentPostmortemStatus.ARCHIVED);
        assertThat(archived.getArchivedAt()).isNotNull();
        assertThat(archived.getPublishedAt()).isEqualTo(published.getPublishedAt());

        IncidentPostmortem unarchived = incidentPostmortemService.unarchivePostmortem(
                ctx.organization.getId(), incident.getId(), draft.getId(), ctx.admin.getId());
        assertThat(unarchived.getStatus()).isEqualTo(IncidentPostmortemStatus.DRAFT);
        assertThat(unarchived.getArchivedAt()).isNull();
        assertThat(unarchived.getPublishedAt()).isEqualTo(published.getPublishedAt());
        assertThat(unarchived.getPublishedBy().getId()).isEqualTo(published.getPublishedBy().getId());
    }

    @Test
    void organizationListDefaultsToPublishedOnly() {
        var ctx = new OrgContext("list");
        Incident resolved1 = createResolvedIncident(ctx, "One");
        Incident resolved2 = createResolvedIncident(ctx, "Two");

        IncidentPostmortem draft = incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), resolved1.getId(), ctx.member.getId(), CreatePostmortemParams.empty());
        IncidentPostmortem toPublish = incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), resolved2.getId(), ctx.member.getId(), CreatePostmortemParams.empty());
        incidentPostmortemService.updateDraft(
                ctx.organization.getId(),
                resolved2.getId(),
                toPublish.getId(),
                ctx.member.getId(),
                PostmortemDraftUpdate.builder().summary("Published summary").rootCause("Cause").build());
        incidentPostmortemService.publishPostmortem(
                ctx.organization.getId(), resolved2.getId(), toPublish.getId(), ctx.member.getId());

        var defaultList = incidentPostmortemService.listOrganizationPostmortems(
                ctx.organization.getId(), ctx.viewer.getId(), null, PageRequest.of(0, 20));
        var draftList = incidentPostmortemService.listOrganizationPostmortems(
                ctx.organization.getId(),
                ctx.viewer.getId(),
                OrganizationPostmortemListFilter.DRAFT,
                PageRequest.of(0, 20));

        assertThat(defaultList.getTotalElements()).isEqualTo(1);
        assertThat(defaultList.getContent().getFirst().getId()).isEqualTo(toPublish.getId());
        assertThat(draftList.getTotalElements()).isEqualTo(1);
        assertThat(draftList.getContent().getFirst().getId()).isEqualTo(draft.getId());
    }

    @Test
    void publishValidationFailureDoesNotChangeStatus() {
        var ctx = new OrgContext("rollback");
        Incident incident = createResolvedIncident(ctx, "Validate");
        IncidentPostmortem draft = incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), CreatePostmortemParams.empty());

        assertThatThrownBy(() -> incidentPostmortemService.publishPostmortem(
                        ctx.organization.getId(), incident.getId(), draft.getId(), ctx.member.getId()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo("POSTMORTEM_PUBLISH_VALIDATION_FAILED");

        IncidentPostmortem reloaded = incidentPostmortemService.getPostmortemByIncident(
                ctx.organization.getId(), incident.getId(), ctx.viewer.getId());
        assertThat(reloaded.getStatus()).isEqualTo(IncidentPostmortemStatus.DRAFT);
        assertThat(reloaded.getPublishedAt()).isNull();
    }

    @Test
    void globalAdminUserRoleDoesNotOverrideOrganizationMemberRole() {
        var ctx = new OrgContext("global");
        User globalAdmin = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "global@example.com");
        globalAdmin.setRole(com.example.incidentmanagement.user.UserRole.ADMIN);
        userRepository.save(globalAdmin);
        addMember(ctx.organization, globalAdmin, OrganizationRole.MEMBER);

        Incident incident = createResolvedIncident(ctx, "Roles");
        IncidentPostmortem draft = incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), incident.getId(), ctx.member.getId(), CreatePostmortemParams.empty());

        assertThatThrownBy(() -> incidentPostmortemService.deleteDraftPostmortem(
                        ctx.organization.getId(), incident.getId(), draft.getId(), globalAdmin.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void draftListUsesCreatedAtOrdering() {
        var ctx = new OrgContext("draft-order");
        Incident first = createResolvedIncident(ctx, "First");
        Incident second = createResolvedIncident(ctx, "Second");

        incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), first.getId(), ctx.member.getId(), CreatePostmortemParams.empty());
        incidentPostmortemService.createPostmortem(
                ctx.organization.getId(), second.getId(), ctx.member.getId(), CreatePostmortemParams.empty());

        var page = incidentPostmortemService.listOrganizationPostmortems(
                ctx.organization.getId(),
                ctx.viewer.getId(),
                OrganizationPostmortemListFilter.DRAFT,
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().getFirst().getIncident().getId()).isEqualTo(second.getId());
    }

    @Test
    void getByIncidentWhenMissingReturnsNotFound() {
        var ctx = new OrgContext("missing");
        Incident incident = createResolvedIncident(ctx, "No pm");

        assertThatThrownBy(() -> incidentPostmortemService.getPostmortemByIncident(
                        ctx.organization.getId(), incident.getId(), ctx.viewer.getId()))
                .isInstanceOf(PostmortemNotFoundException.class);
    }

    private Incident createOpenIncident(OrgContext ctx, String title) {
        return incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams(title, null, IncidentSeverity.SEV3, null, null));
    }

    private Incident createResolvedIncident(OrgContext ctx, String title) {
        Incident incident = createOpenIncident(ctx, title);
        return incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.RESOLVED).build());
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
