package com.example.incidentmanagement.oncall.dto;

import com.example.incidentmanagement.oncall.CreateOnCallScheduleParams;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateOnCallScheduleRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 64) String timezone,
        @NotNull Short rotationLengthDays,
        @NotNull LocalTime handoffLocalTime,
        Short handoffDayOfWeek,
        @NotNull LocalDate effectiveFrom,
        UUID teamId,
        UUID serviceId,
        @NotEmpty List<UUID> participantUserIds) {

    public CreateOnCallScheduleParams toParams() {
        return new CreateOnCallScheduleParams(
                name.trim(),
                timezone.trim(),
                rotationLengthDays,
                handoffLocalTime,
                handoffDayOfWeek,
                effectiveFrom,
                teamId,
                serviceId,
                participantUserIds);
    }
}
