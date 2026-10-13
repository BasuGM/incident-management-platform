package com.example.incidentmanagement.incident;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolved list filters for organization-scoped incident queries. Omitted dimensions are represented by
 * empty {@link Optional}s.
 */
public record IncidentListCriteria(
        Optional<Boolean> active,
        Optional<IncidentStatus> status,
        Optional<IncidentSeverity> severity,
        Optional<UUID> serviceId,
        Optional<UUID> commanderId,
        Optional<String> search) {

    public static IncidentListCriteria empty() {
        return new IncidentListCriteria(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
