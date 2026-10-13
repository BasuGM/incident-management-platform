package com.example.incidentmanagement.oncall;

import java.time.Instant;
import java.util.List;

/**
 * Coverage at a single evaluation instant. {@code currentShift} is {@code null} when the schedule has not
 * started yet (queried instant is strictly before the first handoff).
 */
public record OnCallCoverageResult(
        Instant queriedAt,
        OnCallRotationShift currentShift,
        Instant nextHandoffAt,
        List<OnCallRotationShift> upcomingShifts) {}
