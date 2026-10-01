package com.example.incidentmanagement.organization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.dto.AddOrganizationMemberRequest;
import com.example.incidentmanagement.organization.dto.UpdateOrganizationMemberRequest;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationMembershipServiceTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorizationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private OrganizationMembershipService organizationMembershipService;

    private UUID organizationId;
    private UUID actorId;
    private OrganizationMember ownerActor;
    private Organization organization;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        organization = new Organization();
        organization.setId(organizationId);
        ownerActor = new OrganizationMember();
        ownerActor.setOrganization(organization);
        ownerActor.setRole(OrganizationRole.OWNER);
    }

    @Test
    void addMemberRejectsDuplicateMembership() {
        UUID targetUserId = UUID.randomUUID();
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(actorId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerActor);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId))
                .thenReturn(Optional.of(new OrganizationMember()));

        assertThatThrownBy(() -> organizationMembershipService.addMember(
                        organizationId, new AddOrganizationMemberRequest(targetUserId, OrganizationRole.MEMBER), actorId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void addMemberRejectsOwnerRole() {
        UUID targetUserId = UUID.randomUUID();
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(actorId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerActor);

        assertThatThrownBy(() -> organizationMembershipService.addMember(
                        organizationId, new AddOrganizationMemberRequest(targetUserId, OrganizationRole.OWNER), actorId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateMemberRejectsSelfRoleChange() {
        UUID targetUserId = actorId;
        OrganizationMember target = new OrganizationMember();
        target.setUser(new User());
        target.setRole(OrganizationRole.ADMIN);
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(actorId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerActor);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId))
                .thenReturn(Optional.of(target));

        assertThatThrownBy(() -> organizationMembershipService.updateMemberRole(
                        organizationId,
                        targetUserId,
                        new UpdateOrganizationMemberRequest(OrganizationRole.MEMBER),
                        actorId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeMemberProtectsOnlyOwner() {
        UUID ownerUserId = UUID.randomUUID();
        OrganizationMember target = new OrganizationMember();
        target.setRole(OrganizationRole.OWNER);
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(actorId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerActor);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, ownerUserId))
                .thenReturn(Optional.of(target));
        when(organizationMemberRepository.countByOrganizationIdAndRole(organizationId, OrganizationRole.OWNER))
                .thenReturn(1L);

        assertThatThrownBy(() -> organizationMembershipService.removeMember(organizationId, ownerUserId, actorId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addMemberPersistsWhenValid() {
        UUID targetUserId = UUID.randomUUID();
        User user = new User();
        user.setId(targetUserId);
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(actorId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(ownerActor);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId))
                .thenReturn(Optional.empty());
        when(userService.getById(targetUserId)).thenReturn(user);
        when(organizationMemberRepository.save(any(OrganizationMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        organizationMembershipService.addMember(
                organizationId, new AddOrganizationMemberRequest(targetUserId, OrganizationRole.VIEWER), actorId);

        verify(organizationMemberRepository).save(any(OrganizationMember.class));
    }
}
