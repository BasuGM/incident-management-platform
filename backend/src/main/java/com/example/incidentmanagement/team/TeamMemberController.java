package com.example.incidentmanagement.team;

import com.example.incidentmanagement.security.UserPrincipal;
import com.example.incidentmanagement.team.dto.AddTeamMemberRequest;
import com.example.incidentmanagement.team.dto.TeamMemberResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams/{teamId}/members")
public class TeamMemberController {

    private final TeamMembershipService teamMembershipService;

    public TeamMemberController(TeamMembershipService teamMembershipService) {
        this.teamMembershipService = teamMembershipService;
    }

    @GetMapping
    public List<TeamMemberResponse> listMembers(
            @PathVariable UUID teamId, @AuthenticationPrincipal UserPrincipal principal) {
        return teamMembershipService.listMembers(teamId, principal.getId());
    }

    @PostMapping
    public ResponseEntity<TeamMemberResponse> addMember(
            @PathVariable UUID teamId,
            @Valid @RequestBody AddTeamMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TeamMemberResponse response = teamMembershipService.addMember(teamId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID teamId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        teamMembershipService.removeMember(teamId, userId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
