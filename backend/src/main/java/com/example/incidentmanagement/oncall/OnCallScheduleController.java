package com.example.incidentmanagement.oncall;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.oncall.dto.CreateOnCallScheduleRequest;
import com.example.incidentmanagement.oncall.dto.OnCallCoverageResponse;
import com.example.incidentmanagement.oncall.dto.OnCallScheduleResponse;
import com.example.incidentmanagement.oncall.dto.OnCallScheduleSummaryResponse;
import com.example.incidentmanagement.oncall.dto.UpdateOnCallScheduleRequest;
import com.example.incidentmanagement.security.UserPrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/on-call-schedules")
public class OnCallScheduleController {

    private final OnCallScheduleService onCallScheduleService;
    private final Validator validator;

    public OnCallScheduleController(OnCallScheduleService onCallScheduleService, Validator validator) {
        this.onCallScheduleService = onCallScheduleService;
        this.validator = validator;
    }

    @GetMapping
    public List<OnCallScheduleSummaryResponse> listSchedules(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        OnCallScheduleListFilter filter = parseListFilter(status);
        return onCallScheduleService
                .listSchedules(organizationId, principal.getId(), filter)
                .stream()
                .map(OnCallScheduleSummaryResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<OnCallScheduleResponse> createSchedule(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateOnCallScheduleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        validateRequest(request);
        OnCallScheduleDetail created =
                onCallScheduleService.createSchedule(organizationId, request.toParams(), principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(OnCallScheduleResponse.from(created));
    }

    @GetMapping("/{scheduleId}")
    public OnCallScheduleResponse getSchedule(
            @PathVariable UUID organizationId,
            @PathVariable UUID scheduleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return OnCallScheduleResponse.from(
                onCallScheduleService.getSchedule(organizationId, scheduleId, principal.getId()));
    }

    @PatchMapping("/{scheduleId}")
    public OnCallScheduleResponse updateSchedule(
            @PathVariable UUID organizationId,
            @PathVariable UUID scheduleId,
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserPrincipal principal) {
        UpdateOnCallScheduleRequest request = UpdateOnCallScheduleRequest.parse(body);
        validateRequest(request);
        return OnCallScheduleResponse.from(onCallScheduleService.updateSchedule(
                organizationId, scheduleId, request.toParams(), principal.getId()));
    }

    @PostMapping("/{scheduleId}/archive")
    public OnCallScheduleResponse archiveSchedule(
            @PathVariable UUID organizationId,
            @PathVariable UUID scheduleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return OnCallScheduleResponse.from(
                onCallScheduleService.archiveSchedule(organizationId, scheduleId, principal.getId()));
    }

    @PostMapping("/{scheduleId}/reactivate")
    public OnCallScheduleResponse reactivateSchedule(
            @PathVariable UUID organizationId,
            @PathVariable UUID scheduleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return OnCallScheduleResponse.from(
                onCallScheduleService.reactivateSchedule(organizationId, scheduleId, principal.getId()));
    }

    @GetMapping("/{scheduleId}/coverage")
    public OnCallCoverageResponse getCoverage(
            @PathVariable UUID organizationId,
            @PathVariable UUID scheduleId,
            @RequestParam(required = false) String at,
            @RequestParam(required = false) Integer upcoming,
            @AuthenticationPrincipal UserPrincipal principal) {
        Instant queriedAt = parseInstant(at);
        OnCallScheduleDetail schedule =
                onCallScheduleService.getSchedule(organizationId, scheduleId, principal.getId());
        OnCallCoverageResult coverage = onCallScheduleService.getCoverage(
                organizationId, scheduleId, principal.getId(), queriedAt, upcoming);
        return OnCallCoverageResponse.from(schedule, coverage);
    }

    private static OnCallScheduleListFilter parseListFilter(String status) {
        if (status == null || status.isBlank()) {
            return OnCallScheduleListFilter.ACTIVE;
        }
        try {
            return OnCallScheduleListFilter.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ConflictException(
                    "VALIDATION_ERROR", "Invalid status filter; allowed values: ACTIVE, ARCHIVED, ALL");
        }
    }

    private static Instant parseInstant(String at) {
        if (at == null || at.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(at);
        } catch (DateTimeException ex) {
            throw new ConflictException("VALIDATION_ERROR", "Invalid at parameter; use ISO-8601 instant");
        }
    }

    private void validateRequest(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.iterator().next().getMessage();
            throw new ConflictException("VALIDATION_ERROR", message);
        }
    }
}
