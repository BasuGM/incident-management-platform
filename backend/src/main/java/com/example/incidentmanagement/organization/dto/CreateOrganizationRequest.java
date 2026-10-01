package com.example.incidentmanagement.organization.dto;

import com.example.incidentmanagement.organization.validation.ValidOrganizationSlug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 200) String name, @NotBlank @ValidOrganizationSlug @Size(max = 100) String slug) {}
