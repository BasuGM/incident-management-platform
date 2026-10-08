package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.user.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentCommentService {

    private static final int MAX_BODY_LENGTH = 5000;

    private final IncidentCommentRepository incidentCommentRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public IncidentCommentService(
            IncidentCommentRepository incidentCommentRepository,
            OrganizationAuthorizationService organizationAuthorizationService) {
        this.incidentCommentRepository = incidentCommentRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @Transactional
    public IncidentComment createComment(UUID organizationId, UUID incidentId, UUID callerId, String body) {
        OrganizationMember caller = organizationAuthorizationService.requireRole(
                organizationId,
                callerId,
                OrganizationRole.OWNER,
                OrganizationRole.ADMIN,
                OrganizationRole.MEMBER);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentWritable(incident);

        String normalizedBody = normalizeBody(body);
        IncidentComment comment =
                IncidentComment.create(incident.getOrganization(), incident, caller.getUser(), normalizedBody);
        IncidentComment saved = incidentCommentRepository.save(comment);
        touchAuthor(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<IncidentComment> listComments(
            UUID organizationId, UUID incidentId, UUID callerId, Pageable pageable) {
        organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        return incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                organizationId, incidentId, pageable);
    }

    @Transactional(readOnly = true)
    public IncidentComment getComment(UUID organizationId, UUID incidentId, UUID commentId, UUID callerId) {
        return requireScopedComment(organizationId, incidentId, commentId, callerId);
    }

    @Transactional
    public IncidentComment updateComment(
            UUID organizationId, UUID incidentId, UUID commentId, UUID callerId, String body) {
        organizationAuthorizationService.requireRole(
                organizationId,
                callerId,
                OrganizationRole.OWNER,
                OrganizationRole.ADMIN,
                OrganizationRole.MEMBER);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentWritable(incident);

        IncidentComment comment = requireScopedComment(organizationId, incidentId, commentId, callerId);
        assertCommentActive(comment);

        if (!comment.getAuthor().getId().equals(callerId)) {
            throw new ForbiddenException();
        }

        String normalizedBody = normalizeBody(body);
        comment.updateBody(normalizedBody);
        IncidentComment saved = incidentCommentRepository.save(comment);
        touchAuthor(saved);
        return saved;
    }

    @Transactional
    public IncidentComment deleteComment(UUID organizationId, UUID incidentId, UUID commentId, UUID callerId) {
        OrganizationMember caller = organizationAuthorizationService.requireMembership(organizationId, callerId);
        if (caller.getRole() == OrganizationRole.VIEWER) {
            throw new ForbiddenException();
        }

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentWritable(incident);

        IncidentComment comment = requireScopedComment(organizationId, incidentId, commentId, callerId);
        assertCommentActive(comment);

        boolean isAuthor = comment.getAuthor().getId().equals(callerId);
        boolean canModerate =
                caller.getRole() == OrganizationRole.OWNER || caller.getRole() == OrganizationRole.ADMIN;
        if (!isAuthor && !canModerate) {
            throw new ForbiddenException();
        }

        comment.markDeleted();
        IncidentComment saved = incidentCommentRepository.save(comment);
        touchAuthor(saved);
        return saved;
    }

    private static void touchAuthor(IncidentComment comment) {
        User author = comment.getAuthor();
        author.getId();
        author.getEmail();
        author.getFirstName();
        author.getLastName();
    }

    private IncidentComment requireScopedComment(
            UUID organizationId, UUID incidentId, UUID commentId, UUID callerId) {
        organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        return incidentCommentRepository
                .findByIdAndOrganization_IdAndIncident_Id(commentId, organizationId, incidentId)
                .orElseThrow(ForbiddenException::new);
    }

    private static void assertIncidentWritable(Incident incident) {
        if (incident.getStatus() == IncidentStatus.RESOLVED || incident.getStatus() == IncidentStatus.CANCELLED) {
            throw new ConflictException(
                    "INCIDENT_NOT_EDITABLE", "Incident is in a terminal state and cannot be modified");
        }
    }

    private static void assertCommentActive(IncidentComment comment) {
        if (comment.isDeleted()) {
            throw new ConflictException("COMMENT_NOT_EDITABLE", "Comment cannot be modified");
        }
    }

    private static String normalizeBody(String body) {
        if (body == null) {
            throw new ConflictException("VALIDATION_ERROR", "Comment body is required");
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            throw new ConflictException("VALIDATION_ERROR", "Comment body is required");
        }
        if (trimmed.length() > MAX_BODY_LENGTH) {
            throw new ConflictException("VALIDATION_ERROR", "Comment body must be at most 5000 characters");
        }
        return trimmed;
    }
}
