package com.example.incidentmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.service.dto.CreateServiceRequest;
import com.example.incidentmanagement.service.dto.UpdateServiceRequest;
import com.example.incidentmanagement.team.Team;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ServiceServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorizationService;

    @InjectMocks
    private ServiceService serviceService;

    private UUID organizationId;
    private UUID userId;
    private Organization organization;
    private OrganizationMember ownerMember;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        organization = new Organization();
        organization.setId(organizationId);
        ownerMember = new OrganizationMember();
        ownerMember.setOrganization(organization);
        ownerMember.setRole(OrganizationRole.OWNER);
    }

    @Test
    void createServicePersistsAndReturnsResponse() {
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerMember);
        when(organizationAuthorizationService.requireMembership(organizationId, userId)).thenReturn(ownerMember);
        when(serviceRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "Payments"))
                .thenReturn(false);
        when(serviceRepository.existsByOrganizationIdAndSlug(organizationId, "payments")).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> {
            Service saved = invocation.getArgument(0);
            saved.getId();
            return saved;
        });

        var response = serviceService.createService(
                organizationId, new CreateServiceRequest("Payments", "payments", "Pay", null), userId);

        assertThat(response.name()).isEqualTo("Payments");
        assertThat(response.slug()).isEqualTo("payments");
        verify(serviceRepository).save(any(Service.class));
    }

    @Test
    void createServiceRejectsDuplicateName() {
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerMember);
        when(serviceRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "Payments"))
                .thenReturn(true);

        assertThatThrownBy(() -> serviceService.createService(
                        organizationId, new CreateServiceRequest("Payments", "payments", null, null), userId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createServiceRejectsDuplicateSlug() {
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerMember);
        when(serviceRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "Payments"))
                .thenReturn(false);
        when(serviceRepository.existsByOrganizationIdAndSlug(organizationId, "payments")).thenReturn(true);

        assertThatThrownBy(() -> serviceService.createService(
                        organizationId, new CreateServiceRequest("Payments", "payments", null, null), userId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createServiceRejectsCrossOrgTeam() {
        UUID teamId = UUID.randomUUID();
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerMember);
        when(serviceRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "Payments"))
                .thenReturn(false);
        when(serviceRepository.existsByOrganizationIdAndSlug(organizationId, "payments")).thenReturn(false);
        when(organizationAuthorizationService.requireMembership(organizationId, userId)).thenReturn(ownerMember);
        when(organizationAuthorizationService.requireTeamInOrganization(organizationId, teamId, userId))
                .thenThrow(new ForbiddenException());

        assertThatThrownBy(() -> serviceService.createService(
                        organizationId, new CreateServiceRequest("Payments", "payments", null, teamId), userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void memberCannotCreateService() {
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenThrow(new ForbiddenException());

        assertThatThrownBy(() -> serviceService.createService(
                        organizationId, new CreateServiceRequest("Payments", "payments", null, null), userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listServicesRequiresMembership() {
        Service service = new Service();
        service.setOrganization(organization);
        service.setName("A");
        service.setSlug("a");
        when(organizationAuthorizationService.requireMembership(organizationId, userId)).thenReturn(ownerMember);
        when(serviceRepository.findByOrganizationIdOrderByNameAsc(organizationId)).thenReturn(List.of(service));

        assertThat(serviceService.listServices(organizationId, userId)).hasSize(1);
    }

    @Test
    void getServiceUsesAuthorization() {
        UUID serviceId = UUID.randomUUID();
        Service service = new Service();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setOrganization(organization);
        service.setName("A");
        service.setSlug("a");
        when(organizationAuthorizationService.requireServiceInOrganization(organizationId, serviceId, userId))
                .thenReturn(service);

        assertThat(serviceService.getService(organizationId, serviceId, userId).id()).isEqualTo(serviceId);
    }

    @Test
    void updateServiceAppliesChanges() {
        UUID serviceId = UUID.randomUUID();
        Service service = new Service();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setOrganization(organization);
        service.setName("Old");
        service.setSlug("old");
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerMember);
        when(organizationAuthorizationService.requireServiceInOrganization(organizationId, serviceId, userId))
                .thenReturn(service);
        when(serviceRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "New")).thenReturn(false);
        when(serviceRepository.existsByOrganizationIdAndSlug(organizationId, "new")).thenReturn(false);
        when(serviceRepository.save(service)).thenReturn(service);

        var response = serviceService.updateService(
                organizationId, serviceId, new UpdateServiceRequest("New", "new", "desc", null), userId);

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.slug()).isEqualTo("new");
    }

    @Test
    void memberCannotUpdateService() {
        UUID serviceId = UUID.randomUUID();
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenThrow(new ForbiddenException());

        assertThatThrownBy(() -> serviceService.updateService(
                        organizationId,
                        serviceId,
                        new UpdateServiceRequest("New", null, null, null),
                        userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void viewerCannotDeleteService() {
        UUID serviceId = UUID.randomUUID();
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenThrow(new ForbiddenException());

        assertThatThrownBy(() -> serviceService.deleteService(organizationId, serviceId, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteServiceRemovesEntity() {
        UUID serviceId = UUID.randomUUID();
        Service service = new Service();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setOrganization(organization);
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerMember);
        when(organizationAuthorizationService.requireServiceInOrganization(organizationId, serviceId, userId))
                .thenReturn(service);

        serviceService.deleteService(organizationId, serviceId, userId);

        verify(serviceRepository).delete(service);
    }

    @Test
    void updateServiceAssignsTeamWhenProvided() {
        UUID serviceId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Service service = new Service();
        ReflectionTestUtils.setField(service, "id", serviceId);
        service.setOrganization(organization);
        service.setName("S");
        service.setSlug("s");
        Team team = new Team();
        ReflectionTestUtils.setField(team, "id", teamId);
        team.setName("Platform");
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerMember);
        when(organizationAuthorizationService.requireServiceInOrganization(organizationId, serviceId, userId))
                .thenReturn(service);
        when(organizationAuthorizationService.requireTeamInOrganization(organizationId, teamId, userId))
                .thenReturn(team);
        when(serviceRepository.save(service)).thenReturn(service);

        var response = serviceService.updateService(
                organizationId, serviceId, new UpdateServiceRequest(null, null, null, teamId), userId);

        assertThat(response.teamId()).isEqualTo(teamId);
        assertThat(response.teamName()).isEqualTo("Platform");
    }
}
