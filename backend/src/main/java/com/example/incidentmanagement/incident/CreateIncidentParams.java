package com.example.incidentmanagement.incident;

import java.util.UUID;

/** Internal service-layer input for incident creation (REST DTOs are Step 3). */
public record CreateIncidentParams(
        String title,
        String description,
        IncidentSeverity severity,
        UUID serviceId,
        UUID commanderId) {}
