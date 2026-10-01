package com.example.incidentmanagement.organization.dto;

import com.example.incidentmanagement.organization.validation.ValidOrganizationSlug;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @Size(max = 200) String name, @ValidOrganizationSlug @Size(max = 100) String slug) {}
