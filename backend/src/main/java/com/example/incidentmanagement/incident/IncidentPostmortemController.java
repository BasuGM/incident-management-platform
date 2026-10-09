package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.incident.dto.CreateIncidentPostmortemRequest;
import com.example.incidentmanagement.incident.dto.IncidentPostmortemResponse;
import com.example.incidentmanagement.incident.dto.UpdateIncidentPostmortemRequest;
import com.example.incidentmanagement.security.UserPrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/incidents/{incidentId}/postmortem")
public class IncidentPostmortemController {

    private final IncidentPostmortemService incidentPostmortemService;
    private final Validator validator;

    public IncidentPostmortemController(IncidentPostmortemService incidentPostmortemService, Validator validator) {
        this.incidentPostmortemService = incidentPostmortemService;
        this.validator = validator;
    }

    @GetMapping
    public IncidentPostmortemResponse getPostmortem(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        IncidentPostmortem postmortem =
                incidentPostmortemService.getPostmortemByIncident(organizationId, incidentId, principal.getId());
        return IncidentPostmortemResponse.from(postmortem);
    }

    @PostMapping
    public ResponseEntity<IncidentPostmortemResponse> createPostmortem(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @RequestBody(required = false) CreateIncidentPostmortemRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (request != null) {
            validateRequest(request);
        }
        CreatePostmortemParams params =
                request != null ? request.toParams() : CreatePostmortemParams.empty();
        IncidentPostmortem created = incidentPostmortemService.createPostmortem(
                organizationId, incidentId, principal.getId(), params);
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentPostmortemResponse.from(created));
    }

    @PatchMapping
    public IncidentPostmortemResponse updatePostmortem(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal principal) {
        UpdateIncidentPostmortemRequest request = UpdateIncidentPostmortemRequest.parse(body);
        validateRequest(request);
        UUID postmortemId = incidentPostmortemService
                .getPostmortemByIncident(organizationId, incidentId, principal.getId())
                .getId();
        IncidentPostmortem updated = incidentPostmortemService.updateDraft(
                organizationId, incidentId, postmortemId, principal.getId(), request.toDraftUpdate());
        return IncidentPostmortemResponse.from(updated);
    }

    @DeleteMapping
    public ResponseEntity<Void> deletePostmortem(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID postmortemId = incidentPostmortemService
                .getPostmortemByIncident(organizationId, incidentId, principal.getId())
                .getId();
        incidentPostmortemService.deleteDraftPostmortem(
                organizationId, incidentId, postmortemId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/publish")
    public IncidentPostmortemResponse publishPostmortem(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID postmortemId = incidentPostmortemService
                .getPostmortemByIncident(organizationId, incidentId, principal.getId())
                .getId();
        IncidentPostmortem published = incidentPostmortemService.publishPostmortem(
                organizationId, incidentId, postmortemId, principal.getId());
        return IncidentPostmortemResponse.from(published);
    }

    @PostMapping("/unpublish")
    public IncidentPostmortemResponse unpublishPostmortem(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID postmortemId = incidentPostmortemService
                .getPostmortemByIncident(organizationId, incidentId, principal.getId())
                .getId();
        IncidentPostmortem unpublished = incidentPostmortemService.unpublishPostmortem(
                organizationId, incidentId, postmortemId, principal.getId());
        return IncidentPostmortemResponse.from(unpublished);
    }

    @PostMapping("/archive")
    public IncidentPostmortemResponse archivePostmortem(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID postmortemId = incidentPostmortemService
                .getPostmortemByIncident(organizationId, incidentId, principal.getId())
                .getId();
        IncidentPostmortem archived = incidentPostmortemService.archivePostmortem(
                organizationId, incidentId, postmortemId, principal.getId());
        return IncidentPostmortemResponse.from(archived);
    }

    @PostMapping("/unarchive")
    public IncidentPostmortemResponse unarchivePostmortem(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID postmortemId = incidentPostmortemService
                .getPostmortemByIncident(organizationId, incidentId, principal.getId())
                .getId();
        IncidentPostmortem unarchived = incidentPostmortemService.unarchivePostmortem(
                organizationId, incidentId, postmortemId, principal.getId());
        return IncidentPostmortemResponse.from(unarchived);
    }

    private <T> void validateRequest(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new jakarta.validation.ConstraintViolationException(violations);
        }
    }
}
