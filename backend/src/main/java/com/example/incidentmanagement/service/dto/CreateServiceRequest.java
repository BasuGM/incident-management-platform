package com.example.incidentmanagement.service.dto;

import com.example.incidentmanagement.organization.validation.ValidOrganizationSlug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateServiceRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @ValidOrganizationSlug @Size(max = 100) String slug,
        @Size(max = 500) String description,
        UUID teamId) {}
