package com.example.incidentmanagement.oncall.dto;

import com.example.incidentmanagement.oncall.OnCallScheduleDetail;
import com.example.incidentmanagement.oncall.OnCallScheduleStatus;
import com.example.incidentmanagement.oncall.OnCallScheduleSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record OnCallScheduleSummaryResponse(
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

    public static OnCallScheduleSummaryResponse from(OnCallScheduleSummary summary) {
        return new OnCallScheduleSummaryResponse(
                summary.id(),
                summary.name(),
                summary.status(),
                summary.timezone(),
                summary.rotationLengthDays(),
                summary.handoffLocalTime(),
                summary.handoffDayOfWeek(),
                summary.effectiveFrom(),
                summary.teamId(),
                summary.serviceId(),
                summary.participantCount(),
                summary.createdAt(),
                summary.updatedAt());
    }

    public static OnCallScheduleSummaryResponse fromDetail(OnCallScheduleDetail detail) {
        return from(detail.summary());
    }
}
