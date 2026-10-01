package com.example.incidentmanagement.organization.dto;

import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberResponse(
        UUID id,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        OrganizationRole role,
        Instant createdAt) {

    public static OrganizationMemberResponse from(OrganizationMember member) {
        return new OrganizationMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getFirstName(),
                member.getUser().getLastName(),
                member.getRole(),
                member.getCreatedAt());
    }
}
