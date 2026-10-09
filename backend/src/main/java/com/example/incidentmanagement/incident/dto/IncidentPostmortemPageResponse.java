package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.IncidentPostmortem;
import java.util.List;
import org.springframework.data.domain.Page;

public record IncidentPostmortemPageResponse(
        List<IncidentPostmortemResponse> content, int page, int size, long totalElements, int totalPages) {

    public static IncidentPostmortemPageResponse from(Page<IncidentPostmortem> page) {
        return new IncidentPostmortemPageResponse(
                page.getContent().stream().map(IncidentPostmortemResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
