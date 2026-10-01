package com.example.incidentmanagement.team;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationMemberRepository;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.team.dto.AddTeamMemberRequest;
import com.example.incidentmanagement.user.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamMembershipServiceTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorizationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private TeamMembershipService teamMembershipService;

    @Test
    void addMemberRequiresOrganizationMembership() {
        UUID teamId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Team team = new Team();
        Organization organization = new Organization();
        organization.setId(organizationId);
        team.setOrganization(organization);
        OrganizationMember admin = new OrganizationMember();
        admin.setRole(OrganizationRole.OWNER);

        when(organizationAuthorizationService.requireTeamAccess(teamId, actorId)).thenReturn(team);
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(actorId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(admin);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamMembershipService.addMember(
                        teamId, new AddTeamMemberRequest(targetUserId), actorId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addMemberRejectsDuplicateTeamMembership() {
        UUID teamId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Team team = new Team();
        Organization organization = new Organization();
        organization.setId(organizationId);
        team.setOrganization(organization);
        OrganizationMember admin = new OrganizationMember();
        admin.setRole(OrganizationRole.ADMIN);

        when(organizationAuthorizationService.requireTeamAccess(teamId, actorId)).thenReturn(team);
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(actorId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(admin);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId))
                .thenReturn(Optional.of(new OrganizationMember()));
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, targetUserId)).thenReturn(true);

        assertThatThrownBy(() -> teamMembershipService.addMember(
                        teamId, new AddTeamMemberRequest(targetUserId), actorId))
                .isInstanceOf(ConflictException.class);
    }
}
