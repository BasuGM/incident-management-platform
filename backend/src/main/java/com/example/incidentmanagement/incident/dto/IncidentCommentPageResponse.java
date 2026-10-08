package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.IncidentComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record IncidentCommentPageResponse(
        List<IncidentCommentResponse> content, int page, int size, long totalElements, int totalPages) {

    public static IncidentCommentPageResponse from(Page<IncidentComment> page, UUID organizationId, UUID incidentId) {
        return new IncidentCommentPageResponse(
                page.getContent().stream()
                        .map(comment -> IncidentCommentResponse.from(comment, organizationId, incidentId))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
