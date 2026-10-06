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
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorizationService;

    @Mock
    private IncidentNumberAllocator incidentNumberAllocator;

    @InjectMocks
    private IncidentService incidentService;

    private UUID organizationId;
    private UUID incidentId;
    private UUID memberUserId;
    private Organization organization;
    private User memberUser;
    private OrganizationMember member;
    private OrganizationMember adminMember;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        incidentId = UUID.randomUUID();
        memberUserId = UUID.randomUUID();

        organization = new Organization();
        organization.setId(organizationId);

        memberUser = new User();
        memberUser.setId(memberUserId);

        member = member(OrganizationRole.MEMBER);
        adminMember = member(OrganizationRole.ADMIN);
    }

    private OrganizationMember member(OrganizationRole role) {
        OrganizationMember organizationMember = new OrganizationMember();
        organizationMember.setOrganization(organization);
        organizationMember.setUser(memberUser);
        organizationMember.setRole(role);
        return organizationMember;
    }

    private Incident openIncident() {
        Incident incident = new Incident();
        ReflectionTestUtils.setField(incident, "id", incidentId);
        incident.setOrganization(organization);
        incident.setIncidentNumber(1L);
        incident.setTitle("Outage");
        incident.setSeverity(IncidentSeverity.SEV2);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setReporter(memberUser);
        return incident;
    }

    private void stubSaveAndReload(Incident incident) {
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(incidentRepository.findByIdAndOrganizationId(incidentId, organizationId))
                .thenReturn(Optional.of(incident));
    }

    private void stubSaveAnyAndReload(Incident incident) {
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", incidentId);
            when(incidentRepository.findByIdAndOrganizationId(incidentId, organizationId))
                    .thenReturn(Optional.of(saved));
            return saved;
        });
    }

    @Nested
    class Create {
        @Test
        void createsIncidentWithOpenStatusAndAllocatedNumber() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(incidentNumberAllocator.allocateNextIncidentNumber(organizationId)).thenReturn(7L);
            stubSaveAnyAndReload(openIncident());

            Incident created = incidentService.createIncident(
                    organizationId,
                    memberUserId,
                    new CreateIncidentParams("API down", "details", IncidentSeverity.SEV1, null, null));

            assertThat(created.getStatus()).isEqualTo(IncidentStatus.OPEN);
            assertThat(created.getIncidentNumber()).isEqualTo(7L);
            assertThat(created.getReporter()).isEqualTo(memberUser);
            assertThat(created.getAcknowledgedAt()).isNull();
            verify(incidentNumberAllocator).allocateNextIncidentNumber(organizationId);
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

            assertThatThrownBy(() -> incidentService.createIncident(
                            organizationId,
                            memberUserId,
                            new CreateIncidentParams("x", null, IncidentSeverity.SEV3, null, null)))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void crossOrgServiceRejected() {
            UUID serviceId = UUID.randomUUID();
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireServiceInOrganization(organizationId, serviceId, memberUserId))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentService.createIncident(
                            organizationId,
                            memberUserId,
                            new CreateIncidentParams("x", null, IncidentSeverity.SEV2, serviceId, null)))
                    .isInstanceOf(ForbiddenException.class);
            verify(incidentNumberAllocator, never()).allocateNextIncidentNumber(any());
        }

        @Test
        void crossOrgCommanderRejected() {
            UUID commanderId = UUID.randomUUID();
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireOrganizationMember(organizationId, commanderId, memberUserId))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentService.createIncident(
                            organizationId,
                            memberUserId,
                            new CreateIncidentParams("x", null, IncidentSeverity.SEV2, null, commanderId)))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class GetAndList {
        @Test
        void memberCanRetrieve() {
            Incident incident = openIncident();
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);
            when(incidentRepository.findByIdAndOrganizationId(incidentId, organizationId))
                    .thenReturn(Optional.of(incident));

            assertThat(incidentService.getIncident(organizationId, incidentId, memberUserId)).isEqualTo(incident);
        }

        @Test
        void crossOrgRetrievalRejected() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentService.getIncident(organizationId, incidentId, memberUserId))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void listUsesRepositoryPagination() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Incident> page = new PageImpl<>(List.of(openIncident()));
            when(incidentRepository.findByOrganizationId(organizationId, pageable)).thenReturn(page);

            Page<Incident> result = incidentService.listIncidents(organizationId, memberUserId, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(organizationAuthorizationService).requireOrganizationMember(organizationId, memberUserId);
            verify(incidentRepository).findByOrganizationId(organizationId, pageable);
        }
    }

    @Nested
    class Update {
        @Test
        void memberCanChangeTitle() {
            Incident incident = openIncident();
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);
            stubSaveAndReload(incident);

            Incident updated = incidentService.updateIncident(
                    organizationId,
                    incidentId,
                    memberUserId,
                    IncidentUpdateSpec.builder().title("New title").build());

            assertThat(updated.getTitle()).isEqualTo("New title");
        }

        @Test
        void viewerCannotUpdate() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentService.updateIncident(
                            organizationId,
                            incidentId,
                            memberUserId,
                            IncidentUpdateSpec.builder().title("x").build()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void terminalIncidentCannotBeUpdated() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.RESOLVED);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);

            assertThatThrownBy(() -> incidentService.updateIncident(
                            organizationId,
                            incidentId,
                            memberUserId,
                            IncidentUpdateSpec.builder().title("x").build()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("terminal");
        }

        @Test
        void openToAcknowledgedSetsMilestone() {
            Incident incident = openIncident();
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);
            stubSaveAndReload(incident);

            incidentService.updateIncident(
                    organizationId,
                    incidentId,
                    memberUserId,
                    IncidentUpdateSpec.builder().status(IncidentStatus.ACKNOWLEDGED).build());

            assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
            assertThat(incident.getAcknowledgedAt()).isNotNull();
        }

        @Test
        void acknowledgedToResolvedPreservesAcknowledgedAt() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.ACKNOWLEDGED);
            var acknowledgedAt = java.time.Instant.parse("2026-01-01T00:00:00Z");
            incident.setAcknowledgedAt(acknowledgedAt);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);
            stubSaveAndReload(incident);

            incidentService.updateIncident(
                    organizationId,
                    incidentId,
                    memberUserId,
                    IncidentUpdateSpec.builder().status(IncidentStatus.RESOLVED).build());

            assertThat(incident.getAcknowledgedAt()).isEqualTo(acknowledgedAt);
            assertThat(incident.getResolvedAt()).isNotNull();
        }

        @Test
        void memberCannotCancel() {
            Incident incident = openIncident();
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN)))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentService.updateIncident(
                            organizationId,
                            incidentId,
                            memberUserId,
                            IncidentUpdateSpec.builder().status(IncidentStatus.CANCELLED).build()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void adminCanCancel() {
            Incident incident = openIncident();
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(adminMember);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN)))
                    .thenReturn(adminMember);
            stubSaveAndReload(incident);

            incidentService.updateIncident(
                    organizationId,
                    incidentId,
                    memberUserId,
                    IncidentUpdateSpec.builder().status(IncidentStatus.CANCELLED).build());

            assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CANCELLED);
            assertThat(incident.getCancelledAt()).isNotNull();
        }

        @Test
        void invalidTransitionFails() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.ACKNOWLEDGED);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);

            assertThatThrownBy(() -> incidentService.updateIncident(
                            organizationId,
                            incidentId,
                            memberUserId,
                            IncidentUpdateSpec.builder().status(IncidentStatus.OPEN).build()))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("INVALID_INCIDENT_STATUS_TRANSITION");
        }

        @Test
        void resolvedCannotChangeSeverity() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.RESOLVED);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);

            assertThatThrownBy(() -> incidentService.updateIncident(
                            organizationId,
                            incidentId,
                            memberUserId,
                            IncidentUpdateSpec.builder().severity(IncidentSeverity.SEV1).build()))
                    .isInstanceOf(ConflictException.class);
        }
    }
}
