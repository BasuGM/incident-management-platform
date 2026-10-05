package com.example.incidentmanagement.service;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.service.dto.CreateServiceRequest;
import com.example.incidentmanagement.service.dto.ServiceResponse;
import com.example.incidentmanagement.service.dto.UpdateServiceRequest;
import com.example.incidentmanagement.team.Team;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public ServiceService(
            ServiceRepository serviceRepository, OrganizationAuthorizationService organizationAuthorizationService) {
        this.serviceRepository = serviceRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> listServices(UUID organizationId, UUID userId) {
        organizationAuthorizationService.requireMembership(organizationId, userId);
        return serviceRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(ServiceResponse::from)
                .toList();
    }

    @Transactional
    public ServiceResponse createService(UUID organizationId, CreateServiceRequest request, UUID userId) {
        organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        String name = request.name().trim();
        String slug = request.slug().trim().toLowerCase();
        if (serviceRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)) {
            throw new ConflictException("DUPLICATE_SERVICE", "A service with this name already exists in the organization");
        }
        if (serviceRepository.existsByOrganizationIdAndSlug(organizationId, slug)) {
            throw new ConflictException("DUPLICATE_SERVICE_SLUG", "A service with this slug already exists in the organization");
        }

        Organization organization =
                organizationAuthorizationService.requireMembership(organizationId, userId).getOrganization();
        com.example.incidentmanagement.service.Service service = new com.example.incidentmanagement.service.Service();
        service.setOrganization(organization);
        service.setName(name);
        service.setSlug(slug);
        service.setDescription(request.description() != null ? request.description().trim() : null);
        if (request.teamId() != null) {
            Team team = organizationAuthorizationService.requireTeamInOrganization(
                    organizationId, request.teamId(), userId);
            service.setTeam(team);
        }
        return ServiceResponse.from(serviceRepository.save(service));
    }

    @Transactional(readOnly = true)
    public ServiceResponse getService(UUID organizationId, UUID serviceId, UUID userId) {
        com.example.incidentmanagement.service.Service service =
                organizationAuthorizationService.requireServiceInOrganization(organizationId, serviceId, userId);
        return ServiceResponse.from(service);
    }

    @Transactional
    public ServiceResponse updateService(
            UUID organizationId, UUID serviceId, UpdateServiceRequest request, UUID userId) {
        organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        com.example.incidentmanagement.service.Service service =
                organizationAuthorizationService.requireServiceInOrganization(organizationId, serviceId, userId);

        if (request.name() != null) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(service.getName())
                    && serviceRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)) {
                throw new ConflictException(
                        "DUPLICATE_SERVICE", "A service with this name already exists in the organization");
            }
            service.setName(name);
        }
        if (request.slug() != null) {
            String slug = request.slug().trim().toLowerCase();
            if (!slug.equals(service.getSlug()) && serviceRepository.existsByOrganizationIdAndSlug(organizationId, slug)) {
                throw new ConflictException(
                        "DUPLICATE_SERVICE_SLUG", "A service with this slug already exists in the organization");
            }
            service.setSlug(slug);
        }
        if (request.description() != null) {
            service.setDescription(request.description().trim());
        }
        // PATCH only updates teamId when provided (non-null). Omitted teamId is unchanged; explicit null does not clear the team.
        if (request.teamId() != null) {
            Team team = organizationAuthorizationService.requireTeamInOrganization(
                    organizationId, request.teamId(), userId);
            service.setTeam(team);
        }
        return ServiceResponse.from(serviceRepository.save(service));
    }

    @Transactional
    public void deleteService(UUID organizationId, UUID serviceId, UUID userId) {
        organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        com.example.incidentmanagement.service.Service service =
                organizationAuthorizationService.requireServiceInOrganization(organizationId, serviceId, userId);
        serviceRepository.delete(service);
    }
}
