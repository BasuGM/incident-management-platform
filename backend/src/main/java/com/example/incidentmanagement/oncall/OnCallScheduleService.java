package com.example.incidentmanagement.oncall;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.team.Team;
import com.example.incidentmanagement.team.TeamMemberRepository;
import com.example.incidentmanagement.user.User;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnCallScheduleService {

    static final int MAX_UPCOMING_SHIFTS = 20;
    static final int DEFAULT_UPCOMING_SHIFTS = 3;

    private final OnCallScheduleRepository onCallScheduleRepository;
    private final OnCallScheduleParticipantRepository onCallScheduleParticipantRepository;
    private final OrganizationAuthorizationService organizationAuthorizationService;
    private final TeamMemberRepository teamMemberRepository;
    private final OnCallRotationCalculator rotationCalculator;

    public OnCallScheduleService(
            OnCallScheduleRepository onCallScheduleRepository,
            OnCallScheduleParticipantRepository onCallScheduleParticipantRepository,
            OrganizationAuthorizationService organizationAuthorizationService,
            TeamMemberRepository teamMemberRepository) {
        this.onCallScheduleRepository = onCallScheduleRepository;
        this.onCallScheduleParticipantRepository = onCallScheduleParticipantRepository;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.teamMemberRepository = teamMemberRepository;
        this.rotationCalculator = new OnCallRotationCalculator();
    }

    @Transactional(readOnly = true)
    public List<OnCallScheduleSummary> listSchedules(
            UUID organizationId, UUID userId, OnCallScheduleListFilter filter) {
        organizationAuthorizationService.requireMembership(organizationId, userId);
        OnCallScheduleListFilter effective = filter != null ? filter : OnCallScheduleListFilter.ACTIVE;

        List<OnCallSchedule> schedules;
        if (effective == OnCallScheduleListFilter.ALL) {
            schedules = onCallScheduleRepository.findByOrganization_IdOrderByNameAsc(organizationId);
        } else {
            OnCallScheduleStatus status = effective == OnCallScheduleListFilter.ARCHIVED
                    ? OnCallScheduleStatus.ARCHIVED
                    : OnCallScheduleStatus.ACTIVE;
            schedules = onCallScheduleRepository.findByOrganization_IdAndStatusOrderByNameAsc(organizationId, status);
        }

        return schedules.stream()
                .map(schedule -> OnCallScheduleSummary.from(
                        schedule, (int) onCallScheduleParticipantRepository.countBySchedule_Id(schedule.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OnCallScheduleDetail getSchedule(UUID organizationId, UUID scheduleId, UUID userId) {
        OnCallSchedule schedule = requireSchedule(organizationId, scheduleId, userId);
        return toDetail(schedule);
    }

    @Transactional
    public OnCallScheduleDetail createSchedule(
            UUID organizationId, CreateOnCallScheduleParams params, UUID userId) {
        OrganizationMember actor =
                organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        User actorUser = actor.getUser();
        Organization organization = actor.getOrganization();

        String name = requireNonBlankName(params.name());
        assertNameAvailable(organizationId, name, null);
        validateRotationConfig(
                params.rotationLengthDays(),
                params.handoffLocalTime(),
                params.handoffDayOfWeek(),
                params.effectiveFrom(),
                params.timezone());
        validateScopeExclusivity(params.teamId(), params.serviceId());
        List<UUID> participants = requireDistinctParticipants(params.participantUserIdsInOrder());

        OnCallSchedule schedule = buildNewSchedule(organization, params, name, actorUser);
        applyScope(schedule, organizationId, params.teamId(), params.serviceId(), userId);
        validateParticipantsForSchedule(organizationId, schedule, participants, userId);

        OnCallSchedule saved = saveSchedule(schedule);
        replaceParticipants(saved, participants, organizationId, userId);
        return toDetail(saved);
    }

    @Transactional
    public OnCallScheduleDetail updateSchedule(
            UUID organizationId, UUID scheduleId, UpdateOnCallScheduleParams params, UUID userId) {
        organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        OnCallSchedule schedule = requireSchedule(organizationId, scheduleId, userId);
        assertNotArchived(schedule, "update");

        User actorUser = organizationAuthorizationService.requireMembership(organizationId, userId).getUser();
        schedule.setUpdatedBy(actorUser);

        if (params.name() != null) {
            String name = requireNonBlankName(params.name());
            assertNameAvailable(organizationId, name, schedule.getId());
            schedule.setName(name);
        }
        if (params.timezone() != null) {
            validateTimezone(params.timezone());
            schedule.setTimezone(params.timezone().trim());
        }
        if (params.rotationLengthDays() != null || params.handoffLocalTime() != null || params.handoffDayOfWeek() != null
                || params.effectiveFrom() != null) {
            short rotationDays = params.rotationLengthDays() != null
                    ? params.rotationLengthDays()
                    : schedule.getRotationLengthDays();
            LocalTime handoffTime = params.handoffLocalTime() != null
                    ? params.handoffLocalTime()
                    : schedule.getHandoffLocalTime();
            Short handoffDow = params.handoffDayOfWeek() != null
                    ? params.handoffDayOfWeek()
                    : schedule.getHandoffDayOfWeek();
            LocalDate effectiveFrom = params.effectiveFrom() != null
                    ? params.effectiveFrom()
                    : schedule.getEffectiveFrom();
            String timezone = params.timezone() != null ? params.timezone().trim() : schedule.getTimezone();
            validateRotationConfig(rotationDays, handoffTime, handoffDow, effectiveFrom, timezone);
            schedule.setRotationLengthDays(rotationDays);
            schedule.setHandoffLocalTime(handoffTime);
            schedule.setHandoffDayOfWeek(handoffDow);
            schedule.setEffectiveFrom(effectiveFrom);
        }

        if (Boolean.TRUE.equals(params.clearScope())) {
            schedule.setTeam(null);
            schedule.setService(null);
        } else {
            if (params.teamId() != null && params.serviceId() != null) {
                throw new ConflictException("VALIDATION_ERROR", "Specify teamId or serviceId, not both");
            }
            if (params.teamId() != null) {
                Team team = organizationAuthorizationService.requireTeamInOrganization(
                        organizationId, params.teamId(), userId);
                schedule.setTeam(team);
                schedule.setService(null);
            } else if (params.serviceId() != null) {
                com.example.incidentmanagement.service.Service service =
                        organizationAuthorizationService.requireServiceInOrganization(
                        organizationId, params.serviceId(), userId);
                schedule.setService(service);
                schedule.setTeam(null);
            }
        }

        if (params.participantUserIdsInOrder() != null) {
            List<UUID> participants = requireDistinctParticipants(params.participantUserIdsInOrder());
            validateParticipantsForSchedule(organizationId, schedule, participants, userId);
            saveSchedule(schedule);
            replaceParticipants(schedule, participants, organizationId, userId);
            return toDetail(schedule);
        }

        return toDetail(saveSchedule(schedule));
    }

    @Transactional
    public OnCallScheduleDetail archiveSchedule(UUID organizationId, UUID scheduleId, UUID userId) {
        organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        OnCallSchedule schedule = requireSchedule(organizationId, scheduleId, userId);
        if (schedule.getStatus() == OnCallScheduleStatus.ARCHIVED) {
            return toDetail(schedule);
        }
        schedule.setStatus(OnCallScheduleStatus.ARCHIVED);
        schedule.setUpdatedBy(organizationAuthorizationService.requireMembership(organizationId, userId).getUser());
        return toDetail(saveSchedule(schedule));
    }

    @Transactional
    public OnCallScheduleDetail reactivateSchedule(UUID organizationId, UUID scheduleId, UUID userId) {
        organizationAuthorizationService.requireRole(organizationId, userId, OrganizationRole.OWNER, OrganizationRole.ADMIN);
        OnCallSchedule schedule = requireSchedule(organizationId, scheduleId, userId);
        if (schedule.getStatus() == OnCallScheduleStatus.ACTIVE) {
            return toDetail(schedule);
        }
        long count = onCallScheduleParticipantRepository.countBySchedule_Id(schedule.getId());
        if (count == 0) {
            throw new ConflictException("SCHEDULE_EMPTY", "Cannot reactivate a schedule without participants");
        }
        schedule.setStatus(OnCallScheduleStatus.ACTIVE);
        schedule.setUpdatedBy(organizationAuthorizationService.requireMembership(organizationId, userId).getUser());
        return toDetail(saveSchedule(schedule));
    }

    @Transactional(readOnly = true)
    public OnCallCoverageResult getCoverage(
            UUID organizationId, UUID scheduleId, UUID userId, Instant queriedAt, Integer upcomingCount) {
        OnCallSchedule schedule = requireSchedule(organizationId, scheduleId, userId);
        if (schedule.getStatus() != OnCallScheduleStatus.ACTIVE) {
            throw new ConflictException("SCHEDULE_NOT_ACTIVE", "Coverage is not available for archived schedules");
        }

        Instant at = queriedAt != null ? queriedAt : Instant.now();
        int upcoming = upcomingCount != null ? upcomingCount : DEFAULT_UPCOMING_SHIFTS;
        if (upcoming < 0 || upcoming > MAX_UPCOMING_SHIFTS) {
            throw new ConflictException(
                    "VALIDATION_ERROR", "upcoming must be between 0 and " + MAX_UPCOMING_SHIFTS);
        }

        List<UUID> participantIds = loadParticipantUserIds(schedule.getId());
        if (participantIds.isEmpty()) {
            throw new ConflictException("SCHEDULE_EMPTY", "Schedule has no participants");
        }

        OnCallRotationInput input =
                OnCallRotationCalculator.inputFromSchedule(schedule, participantIds, upcoming);
        try {
            return rotationCalculator.calculate(input, at);
        } catch (IllegalArgumentException ex) {
            throw new ConflictException("VALIDATION_ERROR", ex.getMessage());
        }
    }

    private OnCallSchedule requireSchedule(UUID organizationId, UUID scheduleId, UUID userId) {
        organizationAuthorizationService.requireMembership(organizationId, userId);
        return onCallScheduleRepository
                .findByIdAndOrganization_Id(scheduleId, organizationId)
                .orElseThrow(ForbiddenException::new);
    }

    private OnCallScheduleDetail toDetail(OnCallSchedule schedule) {
        List<OnCallScheduleParticipantView> participants =
                onCallScheduleParticipantRepository
                        .findBySchedule_IdOrderByPositionAsc(schedule.getId())
                        .stream()
                        .map(OnCallScheduleParticipantView::from)
                        .toList();
        int count = participants.size();
        return new OnCallScheduleDetail(OnCallScheduleSummary.from(schedule, count), participants);
    }

    private List<UUID> loadParticipantUserIds(UUID scheduleId) {
        return onCallScheduleParticipantRepository
                .findBySchedule_IdOrderByPositionAsc(scheduleId)
                .stream()
                .map(p -> p.getUser().getId())
                .toList();
    }

    private void replaceParticipants(
            OnCallSchedule schedule, List<UUID> participantUserIds, UUID organizationId, UUID actorUserId) {
        onCallScheduleParticipantRepository.deleteBySchedule_Id(schedule.getId());
        onCallScheduleParticipantRepository.flush();
        int position = 0;
        for (UUID participantUserId : participantUserIds) {
            User user = organizationAuthorizationService
                    .requireOrganizationMember(organizationId, participantUserId, actorUserId)
                    .getUser();
            onCallScheduleParticipantRepository.saveAndFlush(
                    OnCallScheduleParticipant.create(schedule, user, position++));
        }
    }

    private void validateParticipantsForSchedule(
            UUID organizationId, OnCallSchedule schedule, List<UUID> participantUserIds, UUID actorUserId) {
        if (participantUserIds.isEmpty()) {
            throw new ConflictException("SCHEDULE_EMPTY", "At least one participant is required");
        }
        Team team = schedule.getTeam();
        for (UUID participantUserId : participantUserIds) {
            organizationAuthorizationService.requireOrganizationMember(organizationId, participantUserId, actorUserId);
            if (team != null
                    && !teamMemberRepository.existsByTeamIdAndUserId(team.getId(), participantUserId)) {
                throw new ConflictException(
                        "VALIDATION_ERROR", "Participant must be a member of the scoped team");
            }
        }
    }

    private OnCallSchedule buildNewSchedule(
            Organization organization,
            CreateOnCallScheduleParams params,
            String name,
            User actorUser) {
        OnCallSchedule schedule;
        if (params.rotationLengthDays() == 7) {
            schedule = OnCallSchedule.createWeekly(
                    organization,
                    name,
                    params.timezone().trim(),
                    params.handoffLocalTime(),
                    params.handoffDayOfWeek(),
                    params.effectiveFrom(),
                    actorUser);
        } else {
            schedule = OnCallSchedule.createDaily(
                    organization,
                    name,
                    params.timezone().trim(),
                    params.handoffLocalTime(),
                    params.effectiveFrom(),
                    actorUser);
        }
        return schedule;
    }

    private void applyScope(OnCallSchedule schedule, UUID organizationId, UUID teamId, UUID serviceId, UUID userId) {
        validateScopeExclusivity(teamId, serviceId);
        if (teamId != null) {
            schedule.setTeam(organizationAuthorizationService.requireTeamInOrganization(organizationId, teamId, userId));
        } else if (serviceId != null) {
            schedule.setService(
                    organizationAuthorizationService.requireServiceInOrganization(organizationId, serviceId, userId));
        }
    }

    private OnCallSchedule saveSchedule(OnCallSchedule schedule) {
        try {
            return onCallScheduleRepository.saveAndFlush(schedule);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("DUPLICATE_NAME", "A schedule with this name already exists in the organization");
        }
    }

    private static void assertNotArchived(OnCallSchedule schedule, String operation) {
        if (schedule.getStatus() == OnCallScheduleStatus.ARCHIVED) {
            throw new ConflictException("SCHEDULE_NOT_ACTIVE", "Cannot " + operation + " an archived schedule");
        }
    }

    private void assertNameAvailable(UUID organizationId, String name, UUID excludeId) {
        boolean exists = excludeId == null
                ? onCallScheduleRepository.existsByOrganization_IdAndNameIgnoreCase(organizationId, name)
                : onCallScheduleRepository.existsByOrganization_IdAndNameIgnoreCaseAndIdNot(
                        organizationId, name, excludeId);
        if (exists) {
            throw new ConflictException("DUPLICATE_NAME", "A schedule with this name already exists in the organization");
        }
    }

    private static String requireNonBlankName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ConflictException("VALIDATION_ERROR", "Name is required");
        }
        if (name.trim().length() > 100) {
            throw new ConflictException("VALIDATION_ERROR", "Name must be at most 100 characters");
        }
        return name.trim();
    }

    private static List<UUID> requireDistinctParticipants(List<UUID> participantUserIds) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            throw new ConflictException("SCHEDULE_EMPTY", "At least one participant is required");
        }
        Set<UUID> unique = new HashSet<>(participantUserIds);
        if (unique.size() != participantUserIds.size()) {
            throw new ConflictException("VALIDATION_ERROR", "Duplicate participants are not allowed");
        }
        return participantUserIds;
    }

    private static void validateScopeExclusivity(UUID teamId, UUID serviceId) {
        if (teamId != null && serviceId != null) {
            throw new ConflictException("VALIDATION_ERROR", "Specify teamId or serviceId, not both");
        }
    }

    private static void validateRotationConfig(
            short rotationLengthDays,
            LocalTime handoffLocalTime,
            Short handoffDayOfWeek,
            LocalDate effectiveFrom,
            String timezone) {
        if (effectiveFrom == null) {
            throw new ConflictException("VALIDATION_ERROR", "effectiveFrom is required");
        }
        if (handoffLocalTime == null) {
            throw new ConflictException("VALIDATION_ERROR", "handoffLocalTime is required");
        }
        validateTimezone(timezone);
        if (rotationLengthDays != 1 && rotationLengthDays != 7) {
            throw new ConflictException("VALIDATION_ERROR", "rotationLengthDays must be 1 or 7");
        }
        if (rotationLengthDays == 7) {
            if (handoffDayOfWeek == null || handoffDayOfWeek < 1 || handoffDayOfWeek > 7) {
                throw new ConflictException(
                        "VALIDATION_ERROR", "handoffDayOfWeek must be between 1 and 7 for weekly rotations");
            }
        } else if (handoffDayOfWeek != null) {
            throw new ConflictException(
                    "VALIDATION_ERROR", "handoffDayOfWeek must be omitted for daily rotations");
        }
    }

    private static void validateTimezone(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) {
            throw new ConflictException("VALIDATION_ERROR", "timezone is required");
        }
        try {
            ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            throw new ConflictException("VALIDATION_ERROR", "Invalid timezone");
        }
    }
}
