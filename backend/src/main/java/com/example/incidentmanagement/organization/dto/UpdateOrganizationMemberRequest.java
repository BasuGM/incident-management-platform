package com.example.incidentmanagement.organization.dto;

import com.example.incidentmanagement.organization.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record UpdateOrganizationMemberRequest(@NotNull OrganizationRole role) {}
