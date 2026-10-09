package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.common.exception.PostmortemNotFoundException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IncidentPostmortemServiceTest {

    @Mock
    private IncidentPostmortemRepository incidentPostmortemRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorizationService;

    @InjectMocks
    private IncidentPostmortemService incidentPostmortemService;

    private UUID organizationId;
    private UUID incidentId;
    private UUID postmortemId;
    private UUID memberUserId;
    private UUID otherUserId;
    private Organization organization;
    private User memberUser;
    private User otherUser;
    private OrganizationMember member;
    private OrganizationMember adminMember;
    private OrganizationMember ownerMember;
    private OrganizationMember viewerMember;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        incidentId = UUID.randomUUID();
        postmortemId = UUID.randomUUID();
        memberUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        organization = new Organization();
        organization.setId(organizationId);

        memberUser = user(memberUserId);
        otherUser = user(otherUserId);

        member = membership(memberUser, OrganizationRole.MEMBER);
        adminMember = membership(memberUser, OrganizationRole.ADMIN);
        ownerMember = membership(memberUser, OrganizationRole.OWNER);
        viewerMember = membership(memberUser, OrganizationRole.VIEWER);
    }

    private static User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private OrganizationMember membership(User user, OrganizationRole role) {
        OrganizationMember organizationMember = new OrganizationMember();
        organizationMember.setOrganization(organization);
        organizationMember.setUser(user);
        organizationMember.setRole(role);
        return organizationMember;
    }

    private Incident resolvedIncident() {
        Incident incident = new Incident();
        ReflectionTestUtils.setField(incident, "id", incidentId);
        incident.setOrganization(organization);
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setTitle("API outage");
        incident.setSeverity(IncidentSeverity.SEV2);
        return incident;
    }

    private Incident openIncident() {
        Incident incident = resolvedIncident();
        incident.setStatus(IncidentStatus.OPEN);
        return incident;
    }

    private IncidentPostmortem draftPostmortem(User author) {
        IncidentPostmortem postmortem =
                IncidentPostmortem.create(organization, resolvedIncident(), author, "Postmortem: API outage");
        ReflectionTestUtils.setField(postmortem, "id", postmortemId);
        return postmortem;
    }

    private void stubResolvedIncident() {
        when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                .thenReturn(resolvedIncident());
    }

    @Nested
    class Create {

        @Test
        void memberCreatesDraftOnResolvedIncident() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            stubResolvedIncident();
            when(incidentPostmortemRepository.existsByOrganization_IdAndIncident_Id(organizationId, incidentId))
                    .thenReturn(false);
            when(incidentPostmortemRepository.save(any(IncidentPostmortem.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            IncidentPostmortem created = incidentPostmortemService.createPostmortem(
                    organizationId, incidentId, memberUserId, CreatePostmortemParams.empty());

            assertThat(created.getStatus()).isEqualTo(IncidentPostmortemStatus.DRAFT);
            assertThat(created.getAuthor().getId()).isEqualTo(memberUserId);
            assertThat(created.getTitle()).isEqualTo("Postmortem: API outage");
        }

        @Test
        void viewerCannotCreate() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentPostmortemService.createPostmortem(
                            organizationId, incidentId, memberUserId, CreatePostmortemParams.empty()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void rejectsNonResolvedIncident() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(openIncident());

            assertThatThrownBy(() -> incidentPostmortemService.createPostmortem(
                            organizationId, incidentId, memberUserId, CreatePostmortemParams.empty()))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM");
        }

        @Test
        void rejectsDuplicatePostmortem() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            stubResolvedIncident();
            when(incidentPostmortemRepository.existsByOrganization_IdAndIncident_Id(organizationId, incidentId))
                    .thenReturn(true);

            assertThatThrownBy(() -> incidentPostmortemService.createPostmortem(
                            organizationId, incidentId, memberUserId, CreatePostmortemParams.empty()))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("POSTMORTEM_ALREADY_EXISTS");
        }
    }

    @Nested
    class Read {

        @Test
        void viewerCanReadPostmortem() {
            IncidentPostmortem postmortem = draftPostmortem(memberUser);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(resolvedIncident());
            when(incidentPostmortemRepository.findByOrganization_IdAndIncident_Id(organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));

            IncidentPostmortem result =
                    incidentPostmortemService.getPostmortemByIncident(organizationId, incidentId, memberUserId);

            assertThat(result.getId()).isEqualTo(postmortemId);
        }

        @Test
        void missingPostmortemReturnsNotFound() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(resolvedIncident());
            when(incidentPostmortemRepository.findByOrganization_IdAndIncident_Id(organizationId, incidentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentPostmortemService.getPostmortemByIncident(
                            organizationId, incidentId, memberUserId))
                    .isInstanceOf(PostmortemNotFoundException.class);
        }

        @Test
        void wrongPostmortemIdReturnsForbidden() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(resolvedIncident());
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentPostmortemService.getPostmortem(
                            organizationId, incidentId, postmortemId, memberUserId))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class UpdateDraft {

        @Test
        void authorUpdatesOwnDraft() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            stubResolvedIncident();
            IncidentPostmortem postmortem = draftPostmortem(memberUser);
            when(organizationAuthorizationService.requireMembership(organizationId, memberUserId))
                    .thenReturn(member);
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));
            when(incidentPostmortemRepository.save(postmortem)).thenReturn(postmortem);

            IncidentPostmortem updated = incidentPostmortemService.updateDraft(
                    organizationId,
                    incidentId,
                    postmortemId,
                    memberUserId,
                    PostmortemDraftUpdate.builder().summary("Impact summary").build());

            assertThat(updated.getSummary()).isEqualTo("Impact summary");
        }

        @Test
        void memberCannotEditAnotherAuthorsDraft() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            stubResolvedIncident();
            IncidentPostmortem postmortem = draftPostmortem(otherUser);
            when(organizationAuthorizationService.requireMembership(organizationId, memberUserId))
                    .thenReturn(member);
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));

            assertThatThrownBy(() -> incidentPostmortemService.updateDraft(
                            organizationId,
                            incidentId,
                            postmortemId,
                            memberUserId,
                            PostmortemDraftUpdate.builder().summary("Nope").build()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void cannotEditPublishedPostmortem() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            stubResolvedIncident();
            IncidentPostmortem postmortem = draftPostmortem(memberUser);
            postmortem.assignStatus(IncidentPostmortemStatus.PUBLISHED);
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));

            assertThatThrownBy(() -> incidentPostmortemService.updateDraft(
                            organizationId,
                            incidentId,
                            postmortemId,
                            memberUserId,
                            PostmortemDraftUpdate.builder().summary("Nope").build()))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("POSTMORTEM_NOT_EDITABLE");
        }
    }

    @Nested
    class Publish {

        @Test
        void publishRequiresSummaryAndRootCause() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            stubResolvedIncident();
            IncidentPostmortem postmortem = draftPostmortem(memberUser);
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));

            assertThatThrownBy(() -> incidentPostmortemService.publishPostmortem(
                            organizationId, incidentId, postmortemId, memberUserId))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("POSTMORTEM_PUBLISH_VALIDATION_FAILED");

            verify(incidentPostmortemRepository, never()).save(any());
        }

        @Test
        void publishDraftSetsMetadata() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            stubResolvedIncident();
            IncidentPostmortem postmortem = draftPostmortem(memberUser);
            postmortem.updateSummary("What happened");
            postmortem.updateRootCause("Misconfiguration");
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));
            when(incidentPostmortemRepository.save(postmortem)).thenReturn(postmortem);

            IncidentPostmortem published = incidentPostmortemService.publishPostmortem(
                    organizationId, incidentId, postmortemId, memberUserId);

            assertThat(published.getStatus()).isEqualTo(IncidentPostmortemStatus.PUBLISHED);
            assertThat(published.getPublishedAt()).isNotNull();
            assertThat(published.getPublishedBy().getId()).isEqualTo(memberUserId);
        }

        @Test
        void unpublishClearsPublicationMetadata() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            stubResolvedIncident();
            IncidentPostmortem postmortem = draftPostmortem(memberUser);
            postmortem.assignStatus(IncidentPostmortemStatus.PUBLISHED);
            postmortem.assignPublicationMetadata(java.time.Instant.now(), memberUser);
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));
            when(incidentPostmortemRepository.save(postmortem)).thenReturn(postmortem);

            IncidentPostmortem unpublished = incidentPostmortemService.unpublishPostmortem(
                    organizationId, incidentId, postmortemId, memberUserId);

            assertThat(unpublished.getStatus()).isEqualTo(IncidentPostmortemStatus.DRAFT);
            assertThat(unpublished.getPublishedAt()).isNull();
            assertThat(unpublished.getPublishedBy()).isNull();
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void archiveRequiresPublishedStatus() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN)))
                    .thenReturn(adminMember);
            stubResolvedIncident();
            IncidentPostmortem postmortem = draftPostmortem(memberUser);
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));

            assertThatThrownBy(() -> incidentPostmortemService.archivePostmortem(
                            organizationId, incidentId, postmortemId, memberUserId))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("INVALID_POSTMORTEM_STATUS_TRANSITION");
        }

        @Test
        void memberCannotArchive() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN)))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentPostmortemService.archivePostmortem(
                            organizationId, incidentId, postmortemId, memberUserId))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void unarchiveReturnsToDraft() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN)))
                    .thenReturn(adminMember);
            stubResolvedIncident();
            IncidentPostmortem postmortem = draftPostmortem(memberUser);
            postmortem.assignStatus(IncidentPostmortemStatus.ARCHIVED);
            postmortem.assignArchivedAt(java.time.Instant.now());
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));
            when(incidentPostmortemRepository.save(postmortem)).thenReturn(postmortem);

            IncidentPostmortem unarchived = incidentPostmortemService.unarchivePostmortem(
                    organizationId, incidentId, postmortemId, memberUserId);

            assertThat(unarchived.getStatus()).isEqualTo(IncidentPostmortemStatus.DRAFT);
            assertThat(unarchived.getArchivedAt()).isNull();
        }
    }

    @Nested
    class OrganizationListing {

        @Test
        void defaultListFilterIsPublishedOnly() {
            when(organizationAuthorizationService.requireMembership(organizationId, memberUserId))
                    .thenReturn(viewerMember);
            when(incidentPostmortemRepository.findByOrganization_IdAndStatusOrderByPublishedAtDescCreatedAtDescIdDesc(
                            eq(organizationId), eq(IncidentPostmortemStatus.PUBLISHED), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            incidentPostmortemService.listOrganizationPostmortems(
                    organizationId, memberUserId, null, PageRequest.of(0, 20));

            verify(incidentPostmortemRepository)
                    .findByOrganization_IdAndStatusOrderByPublishedAtDescCreatedAtDescIdDesc(
                            organizationId, IncidentPostmortemStatus.PUBLISHED, PageRequest.of(0, 20));
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsTitleOver200Characters() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            stubResolvedIncident();
            IncidentPostmortem postmortem = draftPostmortem(memberUser);
            when(organizationAuthorizationService.requireMembership(organizationId, memberUserId))
                    .thenReturn(member);
            when(incidentPostmortemRepository.findByIdAndOrganization_IdAndIncident_Id(
                            postmortemId, organizationId, incidentId))
                    .thenReturn(Optional.of(postmortem));

            String longTitle = "x".repeat(201);
            assertThatThrownBy(() -> incidentPostmortemService.updateDraft(
                            organizationId,
                            incidentId,
                            postmortemId,
                            memberUserId,
                            PostmortemDraftUpdate.builder().title(longTitle).build()))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("VALIDATION_ERROR");
        }
    }
}
