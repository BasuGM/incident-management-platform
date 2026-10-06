package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateIncidentRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        @NotNull IncidentSeverity severity,
        UUID serviceId,
        UUID commanderId) {}
