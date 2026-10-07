package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.user.User;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncidentEventRecorder {

    private final IncidentEventRepository incidentEventRepository;

    public IncidentEventRecorder(IncidentEventRepository incidentEventRepository) {
        this.incidentEventRepository = incidentEventRepository;
    }

    public void recordCreated(Incident incident, User actor) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", incident.getTitle());
        payload.put("description", incident.getDescription());
        payload.put("severity", incident.getSeverity().name());
        payload.put("status", incident.getStatus().name());
        payload.put("serviceId", serviceIdValue(incident));
        payload.put("commanderId", commanderIdValue(incident));
        persist(incident, actor, IncidentEventType.INCIDENT_CREATED, payload);
    }

    public void recordStatusChanged(
            Incident incident, User actor, IncidentStatus previous, IncidentStatus current) {
        persist(incident, actor, IncidentEventType.STATUS_CHANGED, oldNew(previous.name(), current.name()));
    }

    public void recordSeverityChanged(
            Incident incident, User actor, IncidentSeverity previous, IncidentSeverity current) {
        persist(incident, actor, IncidentEventType.SEVERITY_CHANGED, oldNew(previous.name(), current.name()));
    }

    public void recordServiceChanged(Incident incident, User actor, UUID previous, UUID current) {
        persist(incident, actor, IncidentEventType.SERVICE_CHANGED, oldNew(uuidValue(previous), uuidValue(current)));
    }

    public void recordCommanderChanged(Incident incident, User actor, UUID previous, UUID current) {
        persist(incident, actor, IncidentEventType.COMMANDER_CHANGED, oldNew(uuidValue(previous), uuidValue(current)));
    }

    public void recordTitleChanged(Incident incident, User actor, String previous, String current) {
        persist(incident, actor, IncidentEventType.TITLE_CHANGED, oldNew(previous, current));
    }

    public void recordDescriptionChanged(Incident incident, User actor, String previous, String current) {
        persist(incident, actor, IncidentEventType.DESCRIPTION_CHANGED, oldNew(previous, current));
    }

    private void persist(Incident incident, User actor, IncidentEventType eventType, Map<String, Object> payload) {
        IncidentEvent event = IncidentEvent.create(incident.getOrganization(), incident, actor, eventType, payload);
        incidentEventRepository.save(event);
    }

    private static Map<String, Object> oldNew(Object previous, Object current) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("old", previous);
        payload.put("new", current);
        return payload;
    }

    private static Object uuidValue(UUID id) {
        return id == null ? null : id.toString();
    }

    private static Object serviceIdValue(Incident incident) {
        return incident.getService() == null ? null : incident.getService().getId().toString();
    }

    private static Object commanderIdValue(Incident incident) {
        return incident.getCommander() == null ? null : incident.getCommander().getId().toString();
    }
}
