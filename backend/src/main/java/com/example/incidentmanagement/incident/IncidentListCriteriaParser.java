package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.common.exception.ApiClientException;
import com.example.incidentmanagement.common.exception.ConflictException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class IncidentListCriteriaParser {

    private static final int MAX_SEARCH_LENGTH = 200;

    private IncidentListCriteriaParser() {}

    public static IncidentListCriteria parse(
            String active,
            String status,
            String severity,
            String serviceId,
            String commanderId,
            String mine,
            String search,
            UUID callerId) {
        boolean mineRequested = parseRequiredBoolean(mine, "mine").orElse(false);
        Optional<String> commanderIdRaw = trimToOptional(commanderId);

        if (mineRequested && commanderIdRaw.isPresent()) {
            throw new ConflictException(
                    "VALIDATION_ERROR", "Use mine or commanderId, not both");
        }

        Optional<Boolean> activeFilter = parseRequiredBoolean(active, "active");
        Optional<IncidentStatus> statusFilter = parseStatus(status);
        Optional<IncidentSeverity> severityFilter = parseSeverity(severity);
        Optional<UUID> serviceIdFilter = parseUuid(serviceId, "serviceId");
        Optional<UUID> commanderIdFilter = parseCommanderId(commanderIdRaw, mineRequested, callerId);
        Optional<String> searchFilter = parseSearch(search);

        return new IncidentListCriteria(
                activeFilter, statusFilter, severityFilter, serviceIdFilter, commanderIdFilter, searchFilter);
    }

    private static Optional<UUID> parseCommanderId(
            Optional<String> commanderIdRaw, boolean mineRequested, UUID callerId) {
        if (mineRequested) {
            return Optional.of(callerId);
        }
        if (commanderIdRaw.isEmpty()) {
            return Optional.empty();
        }
        String value = commanderIdRaw.get();
        if ("me".equals(value)) {
            return Optional.of(callerId);
        }
        return Optional.of(parseUuidValue(value, "commanderId"));
    }

    private static Optional<UUID> parseUuid(String raw, String paramName) {
        return trimToOptional(raw).map(value -> parseUuidValue(value, paramName));
    }

    private static UUID parseUuidValue(String value, String paramName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiClientException(
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid " + paramName);
        }
    }

    private static Optional<IncidentStatus> parseStatus(String raw) {
        return trimToOptional(raw).map(value -> {
            try {
                return IncidentStatus.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ConflictException(
                        "VALIDATION_ERROR",
                        "Invalid status filter; allowed values: OPEN, ACKNOWLEDGED, RESOLVED, CANCELLED");
            }
        });
    }

    private static Optional<IncidentSeverity> parseSeverity(String raw) {
        return trimToOptional(raw).map(value -> {
            try {
                return IncidentSeverity.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ConflictException(
                        "VALIDATION_ERROR", "Invalid severity filter; allowed values: SEV1, SEV2, SEV3, SEV4");
            }
        });
    }

    private static Optional<Boolean> parseRequiredBoolean(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        if ("true".equals(raw)) {
            return Optional.of(true);
        }
        if ("false".equals(raw)) {
            return Optional.of(false);
        }
        throw new ConflictException("VALIDATION_ERROR", "Invalid " + paramName + " filter; allowed values: true, false");
    }

    private static Optional<String> parseSearch(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            throw new ConflictException(
                    "VALIDATION_ERROR",
                    "Search filter exceeds maximum length of " + MAX_SEARCH_LENGTH);
        }
        return Optional.of(trimmed);
    }

    private static Optional<String> trimToOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(raw.trim());
    }
}
