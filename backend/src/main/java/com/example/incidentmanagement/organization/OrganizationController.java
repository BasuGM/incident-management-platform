package com.example.incidentmanagement.organization;

import com.example.incidentmanagement.organization.dto.CreateOrganizationRequest;
import com.example.incidentmanagement.organization.dto.OrganizationResponse;
import com.example.incidentmanagement.organization.dto.UpdateOrganizationRequest;
import com.example.incidentmanagement.security.UserPrincipal;
import com.example.incidentmanagement.team.TeamService;
import com.example.incidentmanagement.team.dto.CreateTeamRequest;
import com.example.incidentmanagement.team.dto.TeamResponse;
import com.example.incidentmanagement.team.dto.UpdateTeamRequest;
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
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final TeamService teamService;

    public OrganizationController(OrganizationService organizationService, TeamService teamService) {
        this.organizationService = organizationService;
        this.teamService = teamService;
    }

    @GetMapping
    public List<OrganizationResponse> listOrganizations(@AuthenticationPrincipal UserPrincipal principal) {
        return organizationService.listOrganizationsForUser(principal.getId());
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        OrganizationResponse response = organizationService.createOrganization(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{organizationId}")
    public OrganizationResponse getOrganization(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UserPrincipal principal) {
        return organizationService.getOrganization(organizationId, principal.getId());
    }

    @PatchMapping("/{organizationId}")
    public OrganizationResponse updateOrganization(
            @PathVariable UUID organizationId,
            @Valid @RequestBody UpdateOrganizationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return organizationService.updateOrganization(organizationId, request, principal.getId());
    }

    @GetMapping("/{organizationId}/teams")
    public List<TeamResponse> listTeams(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UserPrincipal principal) {
        return teamService.listTeams(organizationId, principal.getId());
    }

    @PostMapping("/{organizationId}/teams")
    public ResponseEntity<TeamResponse> createTeam(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateTeamRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TeamResponse response = teamService.createTeam(organizationId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{organizationId}/teams/{teamId}")
    public TeamResponse getTeam(
            @PathVariable UUID organizationId,
            @PathVariable UUID teamId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return teamService.getTeam(organizationId, teamId, principal.getId());
    }

    @PatchMapping("/{organizationId}/teams/{teamId}")
    public TeamResponse updateTeam(
            @PathVariable UUID organizationId,
            @PathVariable UUID teamId,
            @Valid @RequestBody UpdateTeamRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return teamService.updateTeam(organizationId, teamId, request, principal.getId());
    }

    @DeleteMapping("/{organizationId}/teams/{teamId}")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable UUID organizationId,
            @PathVariable UUID teamId,
            @AuthenticationPrincipal UserPrincipal principal) {
        teamService.deleteTeam(organizationId, teamId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
