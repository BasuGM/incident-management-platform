package com.example.incidentmanagement.service.dto;

import com.example.incidentmanagement.organization.validation.ValidOrganizationSlug;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateServiceRequest(
        @Size(max = 100) String name,
        @ValidOrganizationSlug @Size(max = 100) String slug,
        @Size(max = 500) String description,
        UUID teamId) {}
