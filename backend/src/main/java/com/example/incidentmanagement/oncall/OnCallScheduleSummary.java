package com.example.incidentmanagement.oncall;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record OnCallScheduleSummary(
        UUID id,
        String name,
        OnCallScheduleStatus status,
        String timezone,
        short rotationLengthDays,
        LocalTime handoffLocalTime,
        Short handoffDayOfWeek,
        LocalDate effectiveFrom,
        UUID teamId,
        UUID serviceId,
        int participantCount,
        Instant createdAt,
        Instant updatedAt) {

    static OnCallScheduleSummary from(OnCallSchedule schedule, int participantCount) {
        return new OnCallScheduleSummary(
                schedule.getId(),
                schedule.getName(),
                schedule.getStatus(),
                schedule.getTimezone(),
                schedule.getRotationLengthDays(),
                schedule.getHandoffLocalTime(),
                schedule.getHandoffDayOfWeek(),
                schedule.getEffectiveFrom(),
                schedule.getTeam() != null ? schedule.getTeam().getId() : null,
                schedule.getService() != null ? schedule.getService().getId() : null,
                participantCount,
                schedule.getCreatedAt(),
                schedule.getUpdatedAt());
    }
}
