package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.IncidentEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record IncidentEventPageResponse(
        List<IncidentEventResponse> content, int page, int size, long totalElements, int totalPages) {

    public static IncidentEventPageResponse from(Page<IncidentEvent> page, UUID organizationId, UUID incidentId) {
        return new IncidentEventPageResponse(
                page.getContent().stream()
                        .map(event -> IncidentEventResponse.from(event, organizationId, incidentId))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
