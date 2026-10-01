package com.example.incidentmanagement.team.dto;

import com.example.incidentmanagement.team.Team;
import java.time.Instant;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getOrganization().getId(),
                team.getName(),
                team.getDescription(),
                team.getCreatedAt(),
                team.getUpdatedAt());
    }
}
