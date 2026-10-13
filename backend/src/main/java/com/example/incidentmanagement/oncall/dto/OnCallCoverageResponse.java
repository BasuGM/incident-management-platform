package com.example.incidentmanagement.oncall.dto;

import com.example.incidentmanagement.oncall.OnCallCoverageResult;
import com.example.incidentmanagement.oncall.OnCallScheduleDetail;
import com.example.incidentmanagement.oncall.OnCallScheduleStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record OnCallCoverageResponse(
        UUID scheduleId,
        OnCallScheduleStatus status,
        String timezone,
        Instant queriedAt,
        OnCallCoverageShiftResponse current,
        Instant nextHandoffAt,
        List<OnCallCoverageShiftResponse> upcoming) {

    public static OnCallCoverageResponse from(
            OnCallScheduleDetail schedule, OnCallCoverageResult coverage) {
        Map<UUID, String> displayNames = schedule.participants().stream()
                .collect(Collectors.toMap(p -> p.userId(), p -> p.displayName(), (a, b) -> a));

        OnCallCoverageShiftResponse current = coverage.currentShift() == null
                ? null
                : OnCallCoverageShiftResponse.from(coverage.currentShift(), displayNames);

        List<OnCallCoverageShiftResponse> upcoming = coverage.upcomingShifts().stream()
                .map(shift -> OnCallCoverageShiftResponse.from(shift, displayNames))
                .toList();

        return new OnCallCoverageResponse(
                schedule.summary().id(),
                schedule.summary().status(),
                schedule.summary().timezone(),
                coverage.queriedAt(),
                current,
                coverage.nextHandoffAt(),
                upcoming);
    }
}
