package com.example.incidentmanagement.organization;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.dto.AddOrganizationMemberRequest;
import com.example.incidentmanagement.organization.dto.OrganizationMemberResponse;
import com.example.incidentmanagement.organization.dto.UpdateOrganizationMemberRequest;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationMembershipService {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final UserService userService;

    public OrganizationMembershipService(
            OrganizationMemberRepository organizationMemberRepository,
            OrganizationAuthorizationService organizationAuthorizationService,
            UserService userService) {
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> listMembers(UUID organizationId, UUID userId) {
        organizationAuthorizationService.requireMembership(organizationId, userId);
        return organizationMemberRepository.findByOrganizationId(organizationId).stream()
                .map(OrganizationMemberResponse::from)
                .toList();
    }

    @Transactional
    public OrganizationMemberResponse addMember(
            UUID organizationId, AddOrganizationMemberRequest request, UUID actorUserId) {
        OrganizationMember actor =
                organizationAuthorizationService.requireRole(
                        organizationId, actorUserId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        validateAssignableRole(actor, request.role(), actorUserId, null);

        if (organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, request.userId())
                .isPresent()) {
            throw new ConflictException("DUPLICATE_MEMBERSHIP", "User is already a member of this organization");
        }

        User user = userService.getById(request.userId());
        OrganizationMember member = new OrganizationMember();
        member.setOrganization(actor.getOrganization());
        member.setUser(user);
        member.setRole(request.role());
        return OrganizationMemberResponse.from(organizationMemberRepository.save(member));
    }

    @Transactional
    public OrganizationMemberResponse updateMemberRole(
            UUID organizationId, UUID targetUserId, UpdateOrganizationMemberRequest request, UUID actorUserId) {
        OrganizationMember actor =
                organizationAuthorizationService.requireRole(
                        organizationId, actorUserId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        OrganizationMember target = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(ForbiddenException::new);

        if (actorUserId.equals(targetUserId) && request.role() != target.getRole()) {
            throw new ForbiddenException("Cannot change your own organization role");
        }

        validateAssignableRole(actor, request.role(), actorUserId, target);
        validateOwnerProtection(organizationId, target, request.role());

        target.setRole(request.role());
        return OrganizationMemberResponse.from(organizationMemberRepository.save(target));
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID targetUserId, UUID actorUserId) {
        OrganizationMember actor =
                organizationAuthorizationService.requireRole(
                        organizationId, actorUserId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        OrganizationMember target = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(ForbiddenException::new);

        if (target.getRole() == OrganizationRole.OWNER) {
            long ownerCount =
                    organizationMemberRepository.countByOrganizationIdAndRole(organizationId, OrganizationRole.OWNER);
            if (ownerCount <= 1) {
                throw new ForbiddenException("Cannot remove the only organization owner");
            }
        }

        if (actor.getRole() == OrganizationRole.ADMIN && target.getRole() == OrganizationRole.OWNER) {
            throw new ForbiddenException("Admins cannot remove organization owners");
        }

        organizationMemberRepository.delete(target);
    }

    private void validateAssignableRole(
            OrganizationMember actor, OrganizationRole newRole, UUID actorUserId, OrganizationMember target) {
        if (newRole == OrganizationRole.OWNER) {
            throw new ForbiddenException("Cannot assign organization owner role");
        }
        if (actor.getRole() == OrganizationRole.ADMIN && target != null && target.getRole() == OrganizationRole.OWNER) {
            throw new ForbiddenException("Admins cannot modify organization owners");
        }
    }

    private void validateOwnerProtection(
            UUID organizationId, OrganizationMember target, OrganizationRole newRole) {
        if (target.getRole() == OrganizationRole.OWNER && newRole != OrganizationRole.OWNER) {
            long ownerCount =
                    organizationMemberRepository.countByOrganizationIdAndRole(organizationId, OrganizationRole.OWNER);
            if (ownerCount <= 1) {
                throw new ForbiddenException("Cannot change role of the only organization owner");
            }
        }
    }
}
