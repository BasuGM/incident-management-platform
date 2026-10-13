package com.example.incidentmanagement.oncall;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Partial update; {@code null} fields are left unchanged. {@code clearScope} removes team/service targeting. */
public record UpdateOnCallScheduleParams(
        String name,
        String timezone,
        Short rotationLengthDays,
        LocalTime handoffLocalTime,
        Short handoffDayOfWeek,
        LocalDate effectiveFrom,
        UUID teamId,
        UUID serviceId,
        Boolean clearScope,
        List<UUID> participantUserIdsInOrder) {}
