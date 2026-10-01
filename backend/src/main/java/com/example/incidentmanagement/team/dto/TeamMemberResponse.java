package com.example.incidentmanagement.team.dto;

import com.example.incidentmanagement.team.TeamMember;
import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(
        UUID id, UUID userId, String email, String firstName, String lastName, Instant createdAt) {

    public static TeamMemberResponse from(TeamMember member) {
        return new TeamMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getFirstName(),
                member.getUser().getLastName(),
                member.getCreatedAt());
    }
}
