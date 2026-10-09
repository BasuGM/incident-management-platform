package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.IncidentPostmortem;
import com.example.incidentmanagement.incident.IncidentPostmortemStatus;
import com.example.incidentmanagement.user.User;
import java.time.Instant;
import java.util.UUID;

public record IncidentPostmortemResponse(
        UUID id,
        UUID organizationId,
        UUID incidentId,
        UUID authorId,
        String authorEmail,
        String authorFirstName,
        String authorLastName,
        IncidentPostmortemStatus status,
        String title,
        String summary,
        String impact,
        String rootCause,
        String resolution,
        String lessonsLearned,
        String correctiveActions,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        UUID publishedById,
        String publishedByEmail,
        String publishedByFirstName,
        String publishedByLastName,
        Instant archivedAt) {

    public static IncidentPostmortemResponse from(IncidentPostmortem postmortem) {
        User author = postmortem.getAuthor();
        User publishedBy = postmortem.getPublishedBy();
        return new IncidentPostmortemResponse(
                postmortem.getId(),
                postmortem.getOrganization().getId(),
                postmortem.getIncident().getId(),
                author.getId(),
                author.getEmail(),
                author.getFirstName(),
                author.getLastName(),
                postmortem.getStatus(),
                postmortem.getTitle(),
                postmortem.getSummary(),
                postmortem.getImpact(),
                postmortem.getRootCause(),
                postmortem.getResolution(),
                postmortem.getLessonsLearned(),
                postmortem.getCorrectiveActions(),
                postmortem.getCreatedAt(),
                postmortem.getUpdatedAt(),
                postmortem.getPublishedAt(),
                publishedBy != null ? publishedBy.getId() : null,
                publishedBy != null ? publishedBy.getEmail() : null,
                publishedBy != null ? publishedBy.getFirstName() : null,
                publishedBy != null ? publishedBy.getLastName() : null,
                postmortem.getArchivedAt());
    }
}
