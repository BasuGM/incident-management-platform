package com.example.incidentmanagement.oncall.dto;

import com.example.incidentmanagement.oncall.UpdateOnCallScheduleParams;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public class UpdateOnCallScheduleRequest {

    @Size(max = 100)
    private String name;

    @Size(max = 64)
    private String timezone;

    private Short rotationLengthDays;
    private LocalTime handoffLocalTime;
    private Short handoffDayOfWeek;
    private LocalDate effectiveFrom;
    private UUID teamId;
    private UUID serviceId;
    private Boolean clearScope;
    private List<UUID> participantUserIds;

    private boolean namePresent;
    private boolean timezonePresent;
    private boolean rotationLengthDaysPresent;
    private boolean handoffLocalTimePresent;
    private boolean handoffDayOfWeekPresent;
    private boolean effectiveFromPresent;
    private boolean teamIdPresent;
    private boolean serviceIdPresent;
    private boolean clearScopePresent;
    private boolean participantUserIdsPresent;

    public static UpdateOnCallScheduleRequest parse(JsonNode node) {
        UpdateOnCallScheduleRequest request = new UpdateOnCallScheduleRequest();
        if (node == null || node.isNull()) {
            return request;
        }
        if (node.has("name")) {
            request.namePresent = true;
            request.name = textOrNull(node.get("name"));
        }
        if (node.has("timezone")) {
            request.timezonePresent = true;
            request.timezone = textOrNull(node.get("timezone"));
        }
        if (node.has("rotationLengthDays")) {
            request.rotationLengthDaysPresent = true;
            if (!node.get("rotationLengthDays").isNull()) {
                request.rotationLengthDays = (short) node.get("rotationLengthDays").asInt();
            }
        }
        if (node.has("handoffLocalTime")) {
            request.handoffLocalTimePresent = true;
            if (!node.get("handoffLocalTime").isNull()) {
                request.handoffLocalTime = LocalTime.parse(node.get("handoffLocalTime").asText());
            }
        }
        if (node.has("handoffDayOfWeek")) {
            request.handoffDayOfWeekPresent = true;
            if (!node.get("handoffDayOfWeek").isNull()) {
                request.handoffDayOfWeek = (short) node.get("handoffDayOfWeek").asInt();
            }
        }
        if (node.has("effectiveFrom")) {
            request.effectiveFromPresent = true;
            if (!node.get("effectiveFrom").isNull()) {
                request.effectiveFrom = LocalDate.parse(node.get("effectiveFrom").asText());
            }
        }
        if (node.has("teamId")) {
            request.teamIdPresent = true;
            if (!node.get("teamId").isNull()) {
                request.teamId = UUID.fromString(node.get("teamId").asText());
            }
        }
        if (node.has("serviceId")) {
            request.serviceIdPresent = true;
            if (!node.get("serviceId").isNull()) {
                request.serviceId = UUID.fromString(node.get("serviceId").asText());
            }
        }
        if (node.has("clearScope")) {
            request.clearScopePresent = true;
            request.clearScope = node.get("clearScope").asBoolean();
        }
        if (node.has("participantUserIds")) {
            request.participantUserIdsPresent = true;
            request.participantUserIds = new java.util.ArrayList<>();
            for (JsonNode idNode : node.get("participantUserIds")) {
                request.participantUserIds.add(UUID.fromString(idNode.asText()));
            }
        }
        return request;
    }

    public UpdateOnCallScheduleParams toParams() {
        return new UpdateOnCallScheduleParams(
                namePresent ? name : null,
                timezonePresent ? timezone : null,
                rotationLengthDaysPresent ? rotationLengthDays : null,
                handoffLocalTimePresent ? handoffLocalTime : null,
                handoffDayOfWeekPresent ? handoffDayOfWeek : null,
                effectiveFromPresent ? effectiveFrom : null,
                teamIdPresent ? teamId : null,
                serviceIdPresent ? serviceId : null,
                clearScopePresent ? clearScope : null,
                participantUserIdsPresent ? participantUserIds : null);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
