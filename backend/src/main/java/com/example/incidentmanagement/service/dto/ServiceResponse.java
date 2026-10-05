package com.example.incidentmanagement.service.dto;

import com.example.incidentmanagement.service.Service;
import com.example.incidentmanagement.team.Team;
import java.time.Instant;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        UUID organizationId,
        String name,
        String slug,
        String description,
        UUID teamId,
        String teamName,
        Instant createdAt,
        Instant updatedAt) {

    public static ServiceResponse from(Service service) {
        Team team = service.getTeam();
        return new ServiceResponse(
                service.getId(),
                service.getOrganization().getId(),
                service.getName(),
                service.getSlug(),
                service.getDescription(),
                team != null ? team.getId() : null,
                team != null ? team.getName() : null,
                service.getCreatedAt(),
                service.getUpdatedAt());
    }
}
