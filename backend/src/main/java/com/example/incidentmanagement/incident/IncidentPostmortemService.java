package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.common.exception.PostmortemNotFoundException;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentPostmortemService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_SECTION_LENGTH = 10000;
    private static final String DEFAULT_TITLE_PREFIX = "Postmortem: ";

    private final IncidentPostmortemRepository incidentPostmortemRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;

    public IncidentPostmortemService(
            IncidentPostmortemRepository incidentPostmortemRepository,
            OrganizationAuthorizationService organizationAuthorizationService) {
        this.incidentPostmortemRepository = incidentPostmortemRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
    }

    @Transactional
    public IncidentPostmortem createPostmortem(
            UUID organizationId, UUID incidentId, UUID callerId, CreatePostmortemParams params) {
        OrganizationMember caller = organizationAuthorizationService.requireRole(
                organizationId,
                callerId,
                OrganizationRole.OWNER,
                OrganizationRole.ADMIN,
                OrganizationRole.MEMBER);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentEligibleForPostmortemWrites(incident);

        if (incidentPostmortemRepository.existsByOrganization_IdAndIncident_Id(organizationId, incidentId)) {
            throw new ConflictException("POSTMORTEM_ALREADY_EXISTS", "A postmortem already exists for this incident");
        }

        String title = resolveTitle(params != null ? params.title() : null, incident.getTitle());
        IncidentPostmortem postmortem =
                IncidentPostmortem.create(incident.getOrganization(), incident, caller.getUser(), title);

        if (params != null) {
            applyOptionalSectionOnCreate(postmortem, params);
        }

        try {
            IncidentPostmortem saved = incidentPostmortemRepository.save(postmortem);
            touchUsers(saved);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    "POSTMORTEM_ALREADY_EXISTS", "A postmortem already exists for this incident");
        }
    }

    @Transactional(readOnly = true)
    public IncidentPostmortem getPostmortemByIncident(UUID organizationId, UUID incidentId, UUID callerId) {
        organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        return incidentPostmortemRepository
                .findByOrganization_IdAndIncident_Id(organizationId, incidentId)
                .orElseThrow(PostmortemNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public IncidentPostmortem getPostmortem(
            UUID organizationId, UUID incidentId, UUID postmortemId, UUID callerId) {
        return requireScopedPostmortem(organizationId, incidentId, postmortemId, callerId);
    }

    @Transactional
    public IncidentPostmortem updateDraft(
            UUID organizationId,
            UUID incidentId,
            UUID postmortemId,
            UUID callerId,
            PostmortemDraftUpdate update) {
        organizationAuthorizationService.requireRole(
                organizationId,
                callerId,
                OrganizationRole.OWNER,
                OrganizationRole.ADMIN,
                OrganizationRole.MEMBER);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentEligibleForPostmortemWrites(incident);

        IncidentPostmortem postmortem = requireScopedPostmortem(organizationId, incidentId, postmortemId, callerId);
        assertDraftEditable(postmortem);
        assertCanEditDraft(postmortem, callerId, organizationId);

        applyDraftUpdate(postmortem, update);
        IncidentPostmortem saved = incidentPostmortemRepository.save(postmortem);
        touchUsers(saved);
        return saved;
    }

    @Transactional
    public IncidentPostmortem publishPostmortem(
            UUID organizationId, UUID incidentId, UUID postmortemId, UUID callerId) {
        OrganizationMember caller = organizationAuthorizationService.requireRole(
                organizationId,
                callerId,
                OrganizationRole.OWNER,
                OrganizationRole.ADMIN,
                OrganizationRole.MEMBER);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentEligibleForPostmortemWrites(incident);

        IncidentPostmortem postmortem = requireScopedPostmortem(organizationId, incidentId, postmortemId, callerId);
        assertCanPublishOrUnpublish(postmortem, caller);

        if (postmortem.getStatus() != IncidentPostmortemStatus.DRAFT) {
            throw new ConflictException(
                    "INVALID_POSTMORTEM_STATUS_TRANSITION",
                    "Only draft postmortems can be published");
        }

        assertPublishValidation(postmortem);

        postmortem.assignStatus(IncidentPostmortemStatus.PUBLISHED);
        postmortem.assignPublicationMetadata(Instant.now(), caller.getUser());
        IncidentPostmortem saved = incidentPostmortemRepository.save(postmortem);
        touchUsers(saved);
        return saved;
    }

    @Transactional
    public IncidentPostmortem unpublishPostmortem(
            UUID organizationId, UUID incidentId, UUID postmortemId, UUID callerId) {
        OrganizationMember caller = organizationAuthorizationService.requireRole(
                organizationId,
                callerId,
                OrganizationRole.OWNER,
                OrganizationRole.ADMIN,
                OrganizationRole.MEMBER);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentEligibleForPostmortemWrites(incident);

        IncidentPostmortem postmortem = requireScopedPostmortem(organizationId, incidentId, postmortemId, callerId);
        assertCanPublishOrUnpublish(postmortem, caller);

        if (postmortem.getStatus() != IncidentPostmortemStatus.PUBLISHED) {
            throw new ConflictException(
                    "INVALID_POSTMORTEM_STATUS_TRANSITION",
                    "Only published postmortems can be unpublished");
        }

        postmortem.assignStatus(IncidentPostmortemStatus.DRAFT);
        postmortem.clearPublicationMetadata();
        IncidentPostmortem saved = incidentPostmortemRepository.save(postmortem);
        touchUsers(saved);
        return saved;
    }

    @Transactional
    public IncidentPostmortem archivePostmortem(
            UUID organizationId, UUID incidentId, UUID postmortemId, UUID callerId) {
        OrganizationMember caller = organizationAuthorizationService.requireRole(
                organizationId, callerId, OrganizationRole.OWNER, OrganizationRole.ADMIN);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentEligibleForPostmortemWrites(incident);

        IncidentPostmortem postmortem = requireScopedPostmortem(organizationId, incidentId, postmortemId, callerId);

        if (postmortem.getStatus() != IncidentPostmortemStatus.PUBLISHED) {
            throw new ConflictException(
                    "INVALID_POSTMORTEM_STATUS_TRANSITION",
                    "Only published postmortems can be archived");
        }

        postmortem.assignStatus(IncidentPostmortemStatus.ARCHIVED);
        postmortem.assignArchivedAt(Instant.now());
        IncidentPostmortem saved = incidentPostmortemRepository.save(postmortem);
        touchUsers(saved);
        return saved;
    }

    @Transactional
    public IncidentPostmortem unarchivePostmortem(
            UUID organizationId, UUID incidentId, UUID postmortemId, UUID callerId) {
        organizationAuthorizationService.requireRole(
                organizationId, callerId, OrganizationRole.OWNER, OrganizationRole.ADMIN);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentEligibleForPostmortemWrites(incident);

        IncidentPostmortem postmortem = requireScopedPostmortem(organizationId, incidentId, postmortemId, callerId);

        if (postmortem.getStatus() != IncidentPostmortemStatus.ARCHIVED) {
            throw new ConflictException(
                    "INVALID_POSTMORTEM_STATUS_TRANSITION",
                    "Only archived postmortems can be unarchived");
        }

        postmortem.assignStatus(IncidentPostmortemStatus.DRAFT);
        postmortem.clearArchivedAt();
        IncidentPostmortem saved = incidentPostmortemRepository.save(postmortem);
        touchUsers(saved);
        return saved;
    }

    @Transactional
    public void deleteDraftPostmortem(UUID organizationId, UUID incidentId, UUID postmortemId, UUID callerId) {
        OrganizationMember caller = organizationAuthorizationService.requireRole(
                organizationId,
                callerId,
                OrganizationRole.OWNER,
                OrganizationRole.ADMIN,
                OrganizationRole.MEMBER);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        assertIncidentEligibleForPostmortemWrites(incident);

        IncidentPostmortem postmortem = requireScopedPostmortem(organizationId, incidentId, postmortemId, callerId);

        if (postmortem.getStatus() != IncidentPostmortemStatus.DRAFT) {
            throw new ConflictException(
                    "POSTMORTEM_NOT_EDITABLE", "Only draft postmortems can be deleted");
        }

        assertCanDeleteDraft(postmortem, caller);
        incidentPostmortemRepository.delete(postmortem);
    }

    @Transactional(readOnly = true)
    public Page<IncidentPostmortem> listOrganizationPostmortems(
            UUID organizationId,
            UUID callerId,
            OrganizationPostmortemListFilter filter,
            Pageable pageable) {
        organizationAuthorizationService.requireMembership(organizationId, callerId);

        OrganizationPostmortemListFilter effectiveFilter =
                filter != null ? filter : OrganizationPostmortemListFilter.PUBLISHED;

        return switch (effectiveFilter) {
            case PUBLISHED -> incidentPostmortemRepository
                    .findByOrganization_IdAndStatusOrderByPublishedAtDescCreatedAtDescIdDesc(
                            organizationId, IncidentPostmortemStatus.PUBLISHED, pageable);
            case DRAFT -> incidentPostmortemRepository.findByOrganization_IdAndStatusOrderByCreatedAtDescIdDesc(
                    organizationId, IncidentPostmortemStatus.DRAFT, pageable);
            case ARCHIVED -> incidentPostmortemRepository
                    .findByOrganization_IdAndStatusOrderByPublishedAtDescCreatedAtDescIdDesc(
                            organizationId, IncidentPostmortemStatus.ARCHIVED, pageable);
            case ALL -> incidentPostmortemRepository.findByOrganization_IdOrderByCreatedAtDescIdDesc(
                    organizationId, pageable);
        };
    }

    private IncidentPostmortem requireScopedPostmortem(
            UUID organizationId, UUID incidentId, UUID postmortemId, UUID callerId) {
        organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        return incidentPostmortemRepository
                .findByIdAndOrganization_IdAndIncident_Id(postmortemId, organizationId, incidentId)
                .orElseThrow(ForbiddenException::new);
    }

    private static void assertIncidentEligibleForPostmortemWrites(Incident incident) {
        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new ConflictException(
                    "INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM",
                    "Postmortem operations require a resolved incident");
        }
    }

    private static void assertDraftEditable(IncidentPostmortem postmortem) {
        if (postmortem.getStatus() != IncidentPostmortemStatus.DRAFT) {
            throw new ConflictException("POSTMORTEM_NOT_EDITABLE", "Only draft postmortems can be edited");
        }
    }

    private void assertCanEditDraft(IncidentPostmortem postmortem, UUID callerId, UUID organizationId) {
        OrganizationMember caller = organizationAuthorizationService.requireMembership(organizationId, callerId);
        if (caller.getRole() == OrganizationRole.OWNER || caller.getRole() == OrganizationRole.ADMIN) {
            return;
        }
        if (!postmortem.getAuthor().getId().equals(callerId)) {
            throw new ForbiddenException();
        }
    }

    private void assertCanPublishOrUnpublish(IncidentPostmortem postmortem, OrganizationMember caller) {
        if (caller.getRole() == OrganizationRole.OWNER || caller.getRole() == OrganizationRole.ADMIN) {
            return;
        }
        if (!postmortem.getAuthor().getId().equals(caller.getUser().getId())) {
            throw new ForbiddenException();
        }
    }

    private void assertCanDeleteDraft(IncidentPostmortem postmortem, OrganizationMember caller) {
        if (caller.getRole() == OrganizationRole.OWNER || caller.getRole() == OrganizationRole.ADMIN) {
            return;
        }
        if (!postmortem.getAuthor().getId().equals(caller.getUser().getId())) {
            throw new ForbiddenException();
        }
    }

    private static void assertPublishValidation(IncidentPostmortem postmortem) {
        List<String> missing = new ArrayList<>();
        if (normalizeOptionalSection(postmortem.getSummary()).isEmpty()) {
            missing.add("summary");
        }
        if (normalizeOptionalSection(postmortem.getRootCause()).isEmpty()) {
            missing.add("rootCause");
        }
        if (!missing.isEmpty()) {
            throw new ConflictException(
                    "POSTMORTEM_PUBLISH_VALIDATION_FAILED",
                    "Publish validation failed for fields: " + String.join(", ", missing));
        }
    }

    private static String resolveTitle(String requestedTitle, String incidentTitle) {
        if (requestedTitle != null) {
            return normalizeTitle(requestedTitle);
        }
        String defaultTitle = DEFAULT_TITLE_PREFIX + incidentTitle;
        if (defaultTitle.length() <= MAX_TITLE_LENGTH) {
            return defaultTitle;
        }
        return defaultTitle.substring(0, MAX_TITLE_LENGTH);
    }

    private void applyOptionalSectionOnCreate(IncidentPostmortem postmortem, CreatePostmortemParams params) {
        if (params.title() != null) {
            postmortem.updateTitle(normalizeTitle(params.title()));
        }
        if (params.summary() != null) {
            postmortem.updateSummary(normalizeOptionalSection(params.summary()));
        }
        if (params.impact() != null) {
            postmortem.updateImpact(normalizeOptionalSection(params.impact()));
        }
        if (params.rootCause() != null) {
            postmortem.updateRootCause(normalizeOptionalSection(params.rootCause()));
        }
        if (params.resolution() != null) {
            postmortem.updateResolution(normalizeOptionalSection(params.resolution()));
        }
        if (params.lessonsLearned() != null) {
            postmortem.updateLessonsLearned(normalizeOptionalSection(params.lessonsLearned()));
        }
        if (params.correctiveActions() != null) {
            postmortem.updateCorrectiveActions(normalizeOptionalSection(params.correctiveActions()));
        }
    }

    private void applyDraftUpdate(IncidentPostmortem postmortem, PostmortemDraftUpdate update) {
        if (update == null) {
            return;
        }
        if (update.isTitleSet()) {
            postmortem.updateTitle(normalizeTitle(update.getTitle()));
        }
        if (update.isSummarySet()) {
            postmortem.updateSummary(normalizeOptionalSection(update.getSummary()));
        }
        if (update.isImpactSet()) {
            postmortem.updateImpact(normalizeOptionalSection(update.getImpact()));
        }
        if (update.isRootCauseSet()) {
            postmortem.updateRootCause(normalizeOptionalSection(update.getRootCause()));
        }
        if (update.isResolutionSet()) {
            postmortem.updateResolution(normalizeOptionalSection(update.getResolution()));
        }
        if (update.isLessonsLearnedSet()) {
            postmortem.updateLessonsLearned(normalizeOptionalSection(update.getLessonsLearned()));
        }
        if (update.isCorrectiveActionsSet()) {
            postmortem.updateCorrectiveActions(normalizeOptionalSection(update.getCorrectiveActions()));
        }
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            throw new ConflictException("VALIDATION_ERROR", "Title is required");
        }
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            throw new ConflictException("VALIDATION_ERROR", "Title is required");
        }
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new ConflictException(
                    "VALIDATION_ERROR", "Title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String normalizeOptionalSection(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_SECTION_LENGTH) {
            throw new ConflictException(
                    "VALIDATION_ERROR",
                    "Postmortem section must be at most " + MAX_SECTION_LENGTH + " characters");
        }
        return trimmed;
    }

    private static void touchUsers(IncidentPostmortem postmortem) {
        User author = postmortem.getAuthor();
        author.getId();
        author.getEmail();
        author.getFirstName();
        author.getLastName();
        User publishedBy = postmortem.getPublishedBy();
        if (publishedBy != null) {
            publishedBy.getId();
            publishedBy.getEmail();
            publishedBy.getFirstName();
            publishedBy.getLastName();
        }
    }
}
