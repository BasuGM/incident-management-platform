package com.example.incidentmanagement.team;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.team.dto.CreateTeamRequest;
import com.example.incidentmanagement.team.dto.TeamResponse;
import com.example.incidentmanagement.team.dto.UpdateTeamRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public TeamService(TeamRepository teamRepository, OrganizationAuthorizationService organizationAuthorizationService) {
        this.teamRepository = teamRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listTeams(UUID organizationId, UUID userId) {
        organizationAuthorizationService.requireMembership(organizationId, userId);
        return teamRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(TeamResponse::from)
                .toList();
    }

    @Transactional
    public TeamResponse createTeam(UUID organizationId, CreateTeamRequest request, UUID userId) {
        organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        if (teamRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, request.name().trim())) {
            throw new ConflictException("DUPLICATE_TEAM", "A team with this name already exists in the organization");
        }

        Organization organization = organizationAuthorizationService.requireMembership(organizationId, userId).getOrganization();
        Team team = new Team();
        team.setOrganization(organization);
        team.setName(request.name().trim());
        team.setDescription(request.description() != null ? request.description().trim() : null);
        return TeamResponse.from(teamRepository.save(team));
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID organizationId, UUID teamId, UUID userId) {
        Team team = organizationAuthorizationService.requireTeamInOrganization(organizationId, teamId, userId);
        return TeamResponse.from(team);
    }

    @Transactional
    public TeamResponse updateTeam(UUID organizationId, UUID teamId, UpdateTeamRequest request, UUID userId) {
        organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        Team team = organizationAuthorizationService.requireTeamInOrganization(organizationId, teamId, userId);

        if (request.name() != null) {
            String name = request.name().trim();
            if (!name.equalsIgnoreCase(team.getName())
                    && teamRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)) {
                throw new ConflictException("DUPLICATE_TEAM", "A team with this name already exists in the organization");
            }
            team.setName(name);
        }
        if (request.description() != null) {
            team.setDescription(request.description().trim());
        }
        return TeamResponse.from(teamRepository.save(team));
    }

    @Transactional
    public void deleteTeam(UUID organizationId, UUID teamId, UUID userId) {
        organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        Team team = organizationAuthorizationService.requireTeamInOrganization(organizationId, teamId, userId);
        teamRepository.delete(team);
    }
}
