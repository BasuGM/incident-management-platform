package com.example.incidentmanagement.organization.dto;

import com.example.incidentmanagement.organization.OrganizationRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddOrganizationMemberRequest(@NotNull UUID userId, @NotNull OrganizationRole role) {}
