package com.example.incidentmanagement.incident;

import java.util.UUID;

/**
 * Partial update specification for incidents. Fields are omitted unless explicitly set on the builder.
 * Service and commander support explicit clear via {@code clearService()} / {@code clearCommander()}.
 */
public final class IncidentUpdateSpec {

    private String title;
    private boolean titleSet;

    private String description;
    private boolean descriptionSet;

    private IncidentSeverity severity;
    private boolean severitySet;

    private UUID serviceId;
    private boolean serviceSet;
    private boolean serviceClear;

    private UUID commanderId;
    private boolean commanderSet;
    private boolean commanderClear;

    private IncidentStatus status;
    private boolean statusSet;

    private IncidentUpdateSpec() {}

    public static Builder builder() {
        return new Builder();
    }

    public boolean isTitleSet() {
        return titleSet;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDescriptionSet() {
        return descriptionSet;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSeveritySet() {
        return severitySet;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public boolean isServiceSet() {
        return serviceSet;
    }

    public boolean isServiceClear() {
        return serviceClear;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public boolean isCommanderSet() {
        return commanderSet;
    }

    public boolean isCommanderClear() {
        return commanderClear;
    }

    public UUID getCommanderId() {
        return commanderId;
    }

    public boolean isStatusSet() {
        return statusSet;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public boolean hasChanges() {
        return titleSet
                || descriptionSet
                || severitySet
                || serviceSet
                || commanderSet
                || statusSet;
    }

    public static final class Builder {
        private final IncidentUpdateSpec spec = new IncidentUpdateSpec();

        public Builder title(String title) {
            spec.titleSet = true;
            spec.title = title;
            return this;
        }

        public Builder description(String description) {
            spec.descriptionSet = true;
            spec.description = description;
            return this;
        }

        public Builder severity(IncidentSeverity severity) {
            spec.severitySet = true;
            spec.severity = severity;
            return this;
        }

        public Builder serviceId(UUID serviceId) {
            spec.serviceSet = true;
            spec.serviceClear = false;
            spec.serviceId = serviceId;
            return this;
        }

        public Builder clearService() {
            spec.serviceSet = true;
            spec.serviceClear = true;
            spec.serviceId = null;
            return this;
        }

        public Builder commanderId(UUID commanderId) {
            spec.commanderSet = true;
            spec.commanderClear = false;
            spec.commanderId = commanderId;
            return this;
        }

        public Builder clearCommander() {
            spec.commanderSet = true;
            spec.commanderClear = true;
            spec.commanderId = null;
            return this;
        }

        public Builder status(IncidentStatus status) {
            spec.statusSet = true;
            spec.status = status;
            return this;
        }

        public IncidentUpdateSpec build() {
            return spec;
        }
    }
}
