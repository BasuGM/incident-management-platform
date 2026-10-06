package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.incident.dto.CreateIncidentRequest;
import com.example.incidentmanagement.incident.dto.IncidentPageResponse;
import com.example.incidentmanagement.incident.dto.IncidentResponse;
import com.example.incidentmanagement.incident.dto.UpdateIncidentRequest;
import com.example.incidentmanagement.security.UserPrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/incidents")
public class IncidentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final IncidentService incidentService;
    private final Validator validator;

    public IncidentController(IncidentService incidentService, Validator validator) {
        this.incidentService = incidentService;
        this.validator = validator;
    }

    @GetMapping
    public IncidentPageResponse listIncidents(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable =
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return IncidentPageResponse.from(incidentService.listIncidents(organizationId, principal.getId(), pageable));
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateIncidentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CreateIncidentParams params = new CreateIncidentParams(
                request.title(),
                request.description(),
                request.severity(),
                request.serviceId(),
                request.commanderId());
        Incident incident = incidentService.createIncident(organizationId, principal.getId(), params);
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentResponse.from(incident));
    }

    @GetMapping("/{incidentId}")
    public IncidentResponse getIncident(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return IncidentResponse.from(incidentService.getIncident(organizationId, incidentId, principal.getId()));
    }

    @PatchMapping("/{incidentId}")
    public IncidentResponse updateIncident(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal principal) {
        UpdateIncidentRequest request = UpdateIncidentRequest.parse(body);
        Set<ConstraintViolation<UpdateIncidentRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        Incident incident = incidentService.updateIncident(
                organizationId, incidentId, principal.getId(), request.toUpdateSpec());
        return IncidentResponse.from(incident);
    }
}
