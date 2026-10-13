package com.example.incidentmanagement.oncall;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Schedule rotation parameters required for coverage calculation (no persistence types). */
public record OnCallRotationInput(
        String timezoneId,
        short rotationLengthDays,
        LocalTime handoffLocalTime,
        Short handoffDayOfWeek,
        LocalDate effectiveFrom,
        List<UUID> participantUserIdsInOrder,
        int upcomingShiftCount) {

    public OnCallRotationInput {
        if (timezoneId == null || timezoneId.isBlank()) {
            throw new IllegalArgumentException("timezoneId is required");
        }
        if (handoffLocalTime == null) {
            throw new IllegalArgumentException("handoffLocalTime is required");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("effectiveFrom is required");
        }
        if (participantUserIdsInOrder == null) {
            throw new IllegalArgumentException("participantUserIdsInOrder is required");
        }
        if (upcomingShiftCount < 0) {
            throw new IllegalArgumentException("upcomingShiftCount must be >= 0");
        }
    }
}
