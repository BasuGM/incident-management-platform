package com.example.incidentmanagement.organization.dto;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationRole;
import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        OrganizationRole currentUserRole,
        Instant createdAt,
        Instant updatedAt) {

    public static OrganizationResponse from(Organization organization, OrganizationRole currentUserRole) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                currentUserRole,
                organization.getCreatedAt(),
                organization.getUpdatedAt());
    }
}
