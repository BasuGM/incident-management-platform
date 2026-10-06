package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.IncidentSeverity;
import com.example.incidentmanagement.incident.IncidentStatus;
import com.example.incidentmanagement.incident.IncidentUpdateSpec;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public class UpdateIncidentRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 10000)
    private String description;

    private IncidentSeverity severity;
    private UUID serviceId;
    private UUID commanderId;
    private IncidentStatus status;

    private boolean titlePresent;
    private boolean descriptionPresent;
    private boolean severityPresent;
    private boolean serviceIdPresent;
    private boolean commanderIdPresent;
    private boolean statusPresent;

    public static UpdateIncidentRequest parse(JsonNode node) {
        UpdateIncidentRequest request = new UpdateIncidentRequest();
        if (node == null || node.isNull()) {
            return request;
        }
        if (node.has("title")) {
            request.titlePresent = true;
            request.title = textOrNull(node.get("title"));
        }
        if (node.has("description")) {
            request.descriptionPresent = true;
            request.description = textOrNull(node.get("description"));
        }
        if (node.has("severity")) {
            request.severityPresent = true;
            JsonNode severityNode = node.get("severity");
            if (severityNode.isNull()) {
                request.severity = null;
            } else {
                request.severity = IncidentSeverity.valueOf(severityNode.asText());
            }
        }
        if (node.has("serviceId")) {
            request.serviceIdPresent = true;
            request.serviceId = uuidOrNull(node.get("serviceId"));
        }
        if (node.has("commanderId")) {
            request.commanderIdPresent = true;
            request.commanderId = uuidOrNull(node.get("commanderId"));
        }
        if (node.has("status")) {
            request.statusPresent = true;
            JsonNode statusNode = node.get("status");
            if (statusNode.isNull()) {
                request.status = null;
            } else {
                request.status = IncidentStatus.valueOf(statusNode.asText());
            }
        }
        return request;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getCommanderId() {
        return commanderId;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public boolean isTitlePresent() {
        return titlePresent;
    }

    public boolean isDescriptionPresent() {
        return descriptionPresent;
    }

    public boolean isSeverityPresent() {
        return severityPresent;
    }

    public boolean isServiceIdPresent() {
        return serviceIdPresent;
    }

    public boolean isCommanderIdPresent() {
        return commanderIdPresent;
    }

    public boolean isStatusPresent() {
        return statusPresent;
    }

    public IncidentUpdateSpec toUpdateSpec() {
        IncidentUpdateSpec.Builder builder = IncidentUpdateSpec.builder();
        if (titlePresent) {
            builder.title(title);
        }
        if (descriptionPresent) {
            builder.description(description);
        }
        if (severityPresent) {
            builder.severity(severity);
        }
        if (serviceIdPresent) {
            if (serviceId == null) {
                builder.clearService();
            } else {
                builder.serviceId(serviceId);
            }
        }
        if (commanderIdPresent) {
            if (commanderId == null) {
                builder.clearCommander();
            } else {
                builder.commanderId(commanderId);
            }
        }
        if (statusPresent) {
            builder.status(status);
        }
        return builder.build();
    }

    private static String textOrNull(JsonNode node) {
        return node.isNull() ? null : node.asText();
    }

    private static UUID uuidOrNull(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        return UUID.fromString(node.asText());
    }
}
