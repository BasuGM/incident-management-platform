package com.example.incidentmanagement.organization;

import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.service.ServiceRepository;
import com.example.incidentmanagement.team.Team;
import com.example.incidentmanagement.team.TeamRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAuthorizationService {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final TeamRepository teamRepository;
    private final ServiceRepository serviceRepository;

    public OrganizationAuthorizationService(
            OrganizationMemberRepository organizationMemberRepository,
            TeamRepository teamRepository,
            ServiceRepository serviceRepository) {
        this.organizationMemberRepository = organizationMemberRepository;
        this.teamRepository = teamRepository;
        this.serviceRepository = serviceRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationMember requireMembership(UUID organizationId, UUID userId) {
        return organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(ForbiddenException::new);
    }

    @Transactional(readOnly = true)
    public OrganizationMember requireRole(UUID organizationId, UUID userId, OrganizationRole... roles) {
        OrganizationMember membership = requireMembership(organizationId, userId);
        Set<OrganizationRole> allowed = Arrays.stream(roles).collect(Collectors.toSet());
        if (!allowed.contains(membership.getRole())) {
            throw new ForbiddenException();
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public Team requireTeamInOrganization(UUID organizationId, UUID teamId, UUID userId) {
        requireMembership(organizationId, userId);
        return teamRepository
                .findByIdAndOrganizationId(teamId, organizationId)
                .orElseThrow(ForbiddenException::new);
    }

    @Transactional(readOnly = true)
    public Team requireTeamAccess(UUID teamId, UUID userId) {
        Team team = teamRepository.findById(teamId).orElseThrow(ForbiddenException::new);
        requireMembership(team.getOrganization().getId(), userId);
        return team;
    }

    @Transactional(readOnly = true)
    public com.example.incidentmanagement.service.Service requireServiceInOrganization(
            UUID organizationId, UUID serviceId, UUID userId) {
        requireMembership(organizationId, userId);
        return serviceRepository
                .findByIdAndOrganizationId(serviceId, organizationId)
                .orElseThrow(ForbiddenException::new);
    }
}
