package com.example.incidentmanagement.oncall;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateOnCallScheduleParams(
        String name,
        String timezone,
        short rotationLengthDays,
        LocalTime handoffLocalTime,
        Short handoffDayOfWeek,
        LocalDate effectiveFrom,
        UUID teamId,
        UUID serviceId,
        List<UUID> participantUserIdsInOrder) {}
