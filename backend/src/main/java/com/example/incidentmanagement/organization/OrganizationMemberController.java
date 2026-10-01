package com.example.incidentmanagement.organization;

import com.example.incidentmanagement.organization.dto.AddOrganizationMemberRequest;
import com.example.incidentmanagement.organization.dto.OrganizationMemberResponse;
import com.example.incidentmanagement.organization.dto.UpdateOrganizationMemberRequest;
import com.example.incidentmanagement.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/members")
public class OrganizationMemberController {

    private final OrganizationMembershipService organizationMembershipService;

    public OrganizationMemberController(OrganizationMembershipService organizationMembershipService) {
        this.organizationMembershipService = organizationMembershipService;
    }

    @GetMapping
    public List<OrganizationMemberResponse> listMembers(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UserPrincipal principal) {
        return organizationMembershipService.listMembers(organizationId, principal.getId());
    }

    @PostMapping
    public ResponseEntity<OrganizationMemberResponse> addMember(
            @PathVariable UUID organizationId,
            @Valid @RequestBody AddOrganizationMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        OrganizationMemberResponse response =
                organizationMembershipService.addMember(organizationId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{userId}")
    public OrganizationMemberResponse updateMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateOrganizationMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return organizationMembershipService.updateMemberRole(
                organizationId, userId, request, principal.getId());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        organizationMembershipService.removeMember(organizationId, userId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
