package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.IncidentEvent;
import com.example.incidentmanagement.incident.IncidentEventType;
import com.example.incidentmanagement.user.User;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IncidentEventResponse(
        UUID id,
        UUID incidentId,
        UUID organizationId,
        UUID actorId,
        String actorEmail,
        String actorFirstName,
        String actorLastName,
        IncidentEventType type,
        Map<String, Object> payload,
        Instant createdAt) {

    public static IncidentEventResponse from(IncidentEvent event, UUID organizationId, UUID incidentId) {
        User actor = event.getActor();
        return new IncidentEventResponse(
                event.getId(),
                incidentId,
                organizationId,
                actor.getId(),
                actor.getEmail(),
                actor.getFirstName(),
                actor.getLastName(),
                event.getEventType(),
                event.getPayload(),
                event.getCreatedAt());
    }
}
