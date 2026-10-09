package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.incident.dto.IncidentPostmortemPageResponse;
import com.example.incidentmanagement.security.UserPrincipal;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/postmortems")
public class OrganizationPostmortemController {

    private static final int MAX_PAGE_SIZE = 100;

    private final IncidentPostmortemService incidentPostmortemService;

    public OrganizationPostmortemController(IncidentPostmortemService incidentPostmortemService) {
        this.incidentPostmortemService = incidentPostmortemService;
    }

    @GetMapping
    public IncidentPostmortemPageResponse listPostmortems(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize);
        OrganizationPostmortemListFilter filter = parseStatusFilter(status);
        return IncidentPostmortemPageResponse.from(incidentPostmortemService.listOrganizationPostmortems(
                organizationId, principal.getId(), filter, pageable));
    }

    private static OrganizationPostmortemListFilter parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return OrganizationPostmortemListFilter.PUBLISHED;
        }
        String normalized = status.trim().toUpperCase();
        for (OrganizationPostmortemListFilter filter : OrganizationPostmortemListFilter.values()) {
            if (filter.name().equals(normalized)) {
                return filter;
            }
        }
        throw new ConflictException(
                "VALIDATION_ERROR",
                "Invalid status filter; allowed values: PUBLISHED, DRAFT, ARCHIVED, ALL");
    }
}
