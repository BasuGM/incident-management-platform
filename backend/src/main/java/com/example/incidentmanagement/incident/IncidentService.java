package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.user.User;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = Map.of(
            IncidentStatus.OPEN,
                    Set.of(IncidentStatus.ACKNOWLEDGED, IncidentStatus.RESOLVED, IncidentStatus.CANCELLED),
            IncidentStatus.ACKNOWLEDGED,
                    Set.of(IncidentStatus.RESOLVED, IncidentStatus.CANCELLED),
            IncidentStatus.RESOLVED, Set.of(),
            IncidentStatus.CANCELLED, Set.of());

    private final IncidentRepository incidentRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final IncidentNumberAllocator incidentNumberAllocator;

    public IncidentService(
            IncidentRepository incidentRepository,
            OrganizationAuthorizationService organizationAuthorizationService,
            IncidentNumberAllocator incidentNumberAllocator) {
        this.incidentRepository = incidentRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.incidentNumberAllocator = incidentNumberAllocator;
    }

    @Transactional
    public Incident createIncident(UUID organizationId, UUID callerId, CreateIncidentParams params) {
        OrganizationMember caller = organizationAuthorizationService.requireRole(
                organizationId,
                callerId,
                OrganizationRole.OWNER,
                OrganizationRole.ADMIN,
                OrganizationRole.MEMBER);

        if (params.severity() == null) {
            throw new ConflictException("VALIDATION_ERROR", "Severity is required");
        }
        String title = requireNonBlankTitle(params.title());

        Organization organization = caller.getOrganization();
        User reporter = caller.getUser();

        com.example.incidentmanagement.service.Service service = null;
        if (params.serviceId() != null) {
            service = organizationAuthorizationService.requireServiceInOrganization(
                    organizationId, params.serviceId(), callerId);
        }

        User commander = null;
        if (params.commanderId() != null) {
            OrganizationMember commanderMember = organizationAuthorizationService.requireOrganizationMember(
                    organizationId, params.commanderId(), callerId);
            commander = commanderMember.getUser();
        }

        long incidentNumber = incidentNumberAllocator.allocateNextIncidentNumber(organizationId);

        Incident incident = new Incident();
        incident.setOrganization(organization);
        incident.setIncidentNumber(incidentNumber);
        incident.setService(service);
        incident.setTitle(title);
        incident.setDescription(trimToNull(params.description()));
        incident.setSeverity(params.severity());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setReporter(reporter);
        incident.setCommander(commander);

        Incident saved = incidentRepository.save(incident);
        return reload(organizationId, saved.getId());
    }

    @Transactional(readOnly = true)
    public Incident getIncident(UUID organizationId, UUID incidentId, UUID callerId) {
        organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
        return reload(organizationId, incidentId);
    }

    @Transactional(readOnly = true)
    public Page<Incident> listIncidents(UUID organizationId, UUID callerId, Pageable pageable) {
        organizationAuthorizationService.requireOrganizationMember(organizationId, callerId);
        return incidentRepository.findByOrganizationId(organizationId, pageable);
    }

    @Transactional
    public Incident updateIncident(
            UUID organizationId, UUID incidentId, UUID callerId, IncidentUpdateSpec update) {
        if (!update.hasChanges()) {
            organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);
            return reload(organizationId, incidentId);
        }

        OrganizationMember caller = organizationAuthorizationService.requireRole(
                organizationId,
                callerId,
                OrganizationRole.OWNER,
                OrganizationRole.ADMIN,
                OrganizationRole.MEMBER);

        Incident incident =
                organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, callerId);

        assertMutable(incident);

        if (update.isTitleSet()) {
            incident.setTitle(requireNonBlankTitle(update.getTitle()));
        }
        if (update.isDescriptionSet()) {
            incident.setDescription(trimToNull(update.getDescription()));
        }
        if (update.isSeveritySet()) {
            if (update.getSeverity() == null) {
                throw new ConflictException("VALIDATION_ERROR", "Severity is required");
            }
            incident.setSeverity(update.getSeverity());
        }
        if (update.isServiceSet()) {
            if (update.isServiceClear()) {
                incident.setService(null);
            } else {
                com.example.incidentmanagement.service.Service service =
                        organizationAuthorizationService.requireServiceInOrganization(
                                organizationId, update.getServiceId(), callerId);
                incident.setService(service);
            }
        }
        if (update.isCommanderSet()) {
            if (update.isCommanderClear()) {
                incident.setCommander(null);
            } else {
                OrganizationMember commanderMember = organizationAuthorizationService.requireOrganizationMember(
                        organizationId, update.getCommanderId(), callerId);
                incident.setCommander(commanderMember.getUser());
            }
        }
        if (update.isStatusSet()) {
            applyStatusTransition(incident, update.getStatus(), caller);
        }

        Incident saved = incidentRepository.save(incident);
        return reload(organizationId, saved.getId());
    }

    private Incident reload(UUID organizationId, UUID incidentId) {
        return incidentRepository
                .findByIdAndOrganizationId(incidentId, organizationId)
                .orElseThrow(() -> new IllegalStateException("Incident not found after save"));
    }

    private void applyStatusTransition(Incident incident, IncidentStatus newStatus, OrganizationMember caller) {
        if (newStatus == null) {
            throw new ConflictException("VALIDATION_ERROR", "Status is required");
        }
        IncidentStatus current = incident.getStatus();
        if (current == newStatus) {
            return;
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(newStatus)) {
            throw new ConflictException(
                    "INVALID_INCIDENT_STATUS_TRANSITION",
                    "Cannot transition incident from " + current + " to " + newStatus);
        }
        if (newStatus == IncidentStatus.CANCELLED) {
            organizationAuthorizationService.requireRole(
                    incident.getOrganization().getId(),
                    caller.getUser().getId(),
                    OrganizationRole.OWNER,
                    OrganizationRole.ADMIN);
        }

        incident.setStatus(newStatus);
        Instant now = Instant.now();
        if (newStatus == IncidentStatus.ACKNOWLEDGED && incident.getAcknowledgedAt() == null) {
            incident.setAcknowledgedAt(now);
        }
        if (newStatus == IncidentStatus.RESOLVED && incident.getResolvedAt() == null) {
            incident.setResolvedAt(now);
        }
        if (newStatus == IncidentStatus.CANCELLED && incident.getCancelledAt() == null) {
            incident.setCancelledAt(now);
        }
    }

    private static void assertMutable(Incident incident) {
        if (incident.getStatus() == IncidentStatus.RESOLVED || incident.getStatus() == IncidentStatus.CANCELLED) {
            throw new ConflictException("INCIDENT_NOT_EDITABLE", "Incident is in a terminal state and cannot be modified");
        }
    }

    private static String requireNonBlankTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ConflictException("VALIDATION_ERROR", "Title is required");
        }
        return title.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
