package com.example.incidentmanagement.organization;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.organization.dto.CreateOrganizationRequest;
import com.example.incidentmanagement.organization.dto.OrganizationResponse;
import com.example.incidentmanagement.organization.dto.UpdateOrganizationRequest;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final UserService userService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            OrganizationAuthorizationService organizationAuthorizationService,
            UserService userService) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.userService = userService;
    }

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, UUID creatorUserId) {
        User creator = userService.getById(creatorUserId);
        String slug = request.slug().trim().toLowerCase();
        if (organizationRepository.existsBySlug(slug)) {
            throw new ConflictException("DUPLICATE_SLUG", "Organization slug already exists");
        }

        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setSlug(slug);
        organization = organizationRepository.save(organization);

        OrganizationMember owner = new OrganizationMember();
        owner.setOrganization(organization);
        owner.setUser(creator);
        owner.setRole(OrganizationRole.OWNER);
        organizationMemberRepository.save(owner);

        return OrganizationResponse.from(organization, OrganizationRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listOrganizationsForUser(UUID userId) {
        return organizationMemberRepository.findOrganizationsForUser(userId).stream()
                .map(org -> {
                    OrganizationRole role = organizationMemberRepository
                            .findByOrganizationIdAndUserId(org.getId(), userId)
                            .map(OrganizationMember::getRole)
                            .orElseThrow();
                    return OrganizationResponse.from(org, role);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(UUID organizationId, UUID userId) {
        OrganizationMember membership = organizationAuthorizationService.requireMembership(organizationId, userId);
        return OrganizationResponse.from(membership.getOrganization(), membership.getRole());
    }

    @Transactional
    public OrganizationResponse updateOrganization(
            UUID organizationId, UpdateOrganizationRequest request, UUID userId) {
        OrganizationMember membership =
                organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER);
        Organization organization = membership.getOrganization();

        if (request.name() != null) {
            organization.setName(request.name().trim());
        }
        if (request.slug() != null) {
            String slug = request.slug().trim().toLowerCase();
            if (!slug.equals(organization.getSlug()) && organizationRepository.existsBySlug(slug)) {
                throw new ConflictException("DUPLICATE_SLUG", "Organization slug already exists");
            }
            organization.setSlug(slug);
        }

        return OrganizationResponse.from(organizationRepository.save(organization), membership.getRole());
    }
}
