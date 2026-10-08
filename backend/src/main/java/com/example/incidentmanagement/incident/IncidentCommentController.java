package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.incident.dto.CreateIncidentCommentRequest;
import com.example.incidentmanagement.incident.dto.IncidentCommentPageResponse;
import com.example.incidentmanagement.incident.dto.IncidentCommentResponse;
import com.example.incidentmanagement.incident.dto.UpdateIncidentCommentRequest;
import com.example.incidentmanagement.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/incidents/{incidentId}/comments")
public class IncidentCommentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final IncidentCommentService incidentCommentService;

    public IncidentCommentController(IncidentCommentService incidentCommentService) {
        this.incidentCommentService = incidentCommentService;
    }

    @GetMapping
    public IncidentCommentPageResponse listComments(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize);
        return IncidentCommentPageResponse.from(
                incidentCommentService.listComments(organizationId, incidentId, principal.getId(), pageable),
                organizationId,
                incidentId);
    }

    @PostMapping
    public ResponseEntity<IncidentCommentResponse> createComment(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @Valid @RequestBody CreateIncidentCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        IncidentComment comment = incidentCommentService.createComment(
                organizationId, incidentId, principal.getId(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IncidentCommentResponse.from(comment, organizationId, incidentId));
    }

    @PatchMapping("/{commentId}")
    public IncidentCommentResponse updateComment(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateIncidentCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        IncidentComment comment = incidentCommentService.updateComment(
                organizationId, incidentId, commentId, principal.getId(), request.body());
        return IncidentCommentResponse.from(comment, organizationId, incidentId);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID organizationId,
            @PathVariable UUID incidentId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        incidentCommentService.deleteComment(organizationId, incidentId, commentId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
