package com.example.incidentmanagement.oncall.dto;

import com.example.incidentmanagement.oncall.OnCallRotationShift;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OnCallCoverageShiftResponse(
        UUID userId, String displayName, Instant shiftStartsAt, Instant shiftEndsAt) {

    static OnCallCoverageShiftResponse from(OnCallRotationShift shift, Map<UUID, String> displayNames) {
        return new OnCallCoverageShiftResponse(
                shift.participantUserId(),
                displayNames.getOrDefault(shift.participantUserId(), "Unknown"),
                shift.shiftStartsAt(),
                shift.shiftEndsAt());
    }
}
