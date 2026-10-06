package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.Incident;
import com.example.incidentmanagement.incident.IncidentSeverity;
import com.example.incidentmanagement.incident.IncidentStatus;
import com.example.incidentmanagement.service.Service;
import com.example.incidentmanagement.user.User;
import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        UUID organizationId,
        long incidentNumber,
        String displayId,
        String title,
        String description,
        IncidentSeverity severity,
        IncidentStatus status,
        UUID serviceId,
        String serviceName,
        UUID reporterId,
        String reporterEmail,
        String reporterFirstName,
        String reporterLastName,
        UUID commanderId,
        String commanderEmail,
        String commanderFirstName,
        String commanderLastName,
        Instant createdAt,
        Instant updatedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant cancelledAt) {

    public static IncidentResponse from(Incident incident) {
        Service service = incident.getService();
        User reporter = incident.getReporter();
        User commander = incident.getCommander();
        return new IncidentResponse(
                incident.getId(),
                incident.getOrganization().getId(),
                incident.getIncidentNumber(),
                "INC-" + incident.getIncidentNumber(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getSeverity(),
                incident.getStatus(),
                service != null ? service.getId() : null,
                service != null ? service.getName() : null,
                reporter.getId(),
                reporter.getEmail(),
                reporter.getFirstName(),
                reporter.getLastName(),
                commander != null ? commander.getId() : null,
                commander != null ? commander.getEmail() : null,
                commander != null ? commander.getFirstName() : null,
                commander != null ? commander.getLastName() : null,
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getAcknowledgedAt(),
                incident.getResolvedAt(),
                incident.getCancelledAt());
    }
}
