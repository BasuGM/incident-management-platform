package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.Incident;
import java.util.List;
import org.springframework.data.domain.Page;

public record IncidentPageResponse(
        List<IncidentResponse> content, int page, int size, long totalElements, int totalPages) {

    public static IncidentPageResponse from(Page<Incident> page) {
        return new IncidentPageResponse(
                page.getContent().stream().map(IncidentResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
