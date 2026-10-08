package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.IncidentComment;
import com.example.incidentmanagement.user.User;
import java.time.Instant;
import java.util.UUID;

public record IncidentCommentResponse(
        UUID id,
        UUID organizationId,
        UUID incidentId,
        UUID authorId,
        String authorEmail,
        String authorFirstName,
        String authorLastName,
        String body,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public static IncidentCommentResponse from(IncidentComment comment, UUID organizationId, UUID incidentId) {
        User author = comment.getAuthor();
        return new IncidentCommentResponse(
                comment.getId(),
                organizationId,
                incidentId,
                author.getId(),
                author.getEmail(),
                author.getFirstName(),
                author.getLastName(),
                comment.getBody(),
                comment.isDeleted(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getDeletedAt());
    }
}
