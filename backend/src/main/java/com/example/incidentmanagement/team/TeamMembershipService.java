package com.example.incidentmanagement.team;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMemberRepository;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.team.dto.AddTeamMemberRequest;
import com.example.incidentmanagement.team.dto.TeamMemberResponse;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamMembershipService {

    private final TeamMemberRepository teamMemberRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final UserService userService;

    public TeamMembershipService(
            TeamMemberRepository teamMemberRepository,
            OrganizationMemberRepository organizationMemberRepository,
            OrganizationAuthorizationService organizationAuthorizationService,
            UserService userService) {
        this.teamMemberRepository = teamMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(UUID teamId, UUID userId) {
        Team team = organizationAuthorizationService.requireTeamAccess(teamId, userId);
        return teamMemberRepository.findByTeamId(team.getId()).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional
    public TeamMemberResponse addMember(UUID teamId, AddTeamMemberRequest request, UUID actorUserId) {
        Team team = organizationAuthorizationService.requireTeamAccess(teamId, actorUserId);
        UUID organizationId = team.getOrganization().getId();
        organizationAuthorizationService.requireRole(organizationId, actorUserId, OrganizationRole.OWNER, OrganizationRole.ADMIN);

        if (!organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, request.userId())
                .isPresent()) {
            throw new ForbiddenException("User must be an organization member before joining a team");
        }

        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, request.userId())) {
            throw new ConflictException("DUPLICATE_TEAM_MEMBERSHIP", "User is already a member of this team");
        }

        User user = userService.getById(request.userId());
        TeamMember member = new TeamMember();
        member.setTeam(team);
        member.setUser(user);
        return TeamMemberResponse.from(teamMemberRepository.save(member));
    }

    @Transactional
    public void removeMember(UUID teamId, UUID targetUserId, UUID actorUserId) {
        Team team = organizationAuthorizationService.requireTeamAccess(teamId, actorUserId);
        organizationAuthorizationService.requireRole(
                team.getOrganization().getId(), actorUserId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        TeamMember member = teamMemberRepository
                .findByTeamIdAndUserId(teamId, targetUserId)
                .orElseThrow(ForbiddenException::new);
        teamMemberRepository.delete(member);
    }
}
