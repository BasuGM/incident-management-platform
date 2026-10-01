package com.example.incidentmanagement.team;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.team.dto.CreateTeamRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorizationService;

    @InjectMocks
    private TeamService teamService;

    @Test
    void createTeamRejectsDuplicateName() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        OrganizationMember member = new OrganizationMember();
        member.setOrganization(organization);

        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(userId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(member);
        when(teamRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "Payments"))
                .thenReturn(true);

        assertThatThrownBy(() -> teamService.createTeam(
                        organizationId, new CreateTeamRequest("Payments", "desc"), userId))
                .isInstanceOf(ConflictException.class);
    }
}
