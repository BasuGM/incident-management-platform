package com.example.incidentmanagement.oncall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.team.TeamMemberRepository;
import com.example.incidentmanagement.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnCallScheduleServiceTest {

    @Mock
    private OnCallScheduleRepository onCallScheduleRepository;

    @Mock
    private OnCallScheduleParticipantRepository onCallScheduleParticipantRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorizationService;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @InjectMocks
    private OnCallScheduleService onCallScheduleService;

    private UUID organizationId;
    private UUID scheduleId;
    private UUID adminId;
    private Organization organization;
    private User adminUser;
    private OrganizationMember adminMember;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        scheduleId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        organization = new Organization();
        organization.setId(organizationId);

        adminUser = new User();
        adminUser.setId(adminId);
        adminUser.setFirstName("Admin");
        adminUser.setLastName("User");

        adminMember = new OrganizationMember();
        adminMember.setOrganization(organization);
        adminMember.setUser(adminUser);
        adminMember.setRole(OrganizationRole.ADMIN);
    }

    @Test
    void getCoverageDelegatesToCalculatorWithOrderedParticipants() {
        OnCallSchedule schedule = activeSchedule();
        UUID participant = UUID.randomUUID();

        when(organizationAuthorizationService.requireMembership(organizationId, adminId)).thenReturn(adminMember);
        when(onCallScheduleRepository.findByIdAndOrganization_Id(scheduleId, organizationId))
                .thenReturn(Optional.of(schedule));
        when(onCallScheduleParticipantRepository.findBySchedule_IdOrderByPositionAsc(scheduleId))
                .thenReturn(List.of(participantRow(schedule, participant, 0)));

        Instant at = Instant.parse("2026-06-01T12:00:00Z");
        OnCallCoverageResult result =
                onCallScheduleService.getCoverage(organizationId, scheduleId, adminId, at, 1);

        assertThat(result.queriedAt()).isEqualTo(at);
        assertThat(result.currentShift().participantUserId()).isEqualTo(participant);
        verify(onCallScheduleParticipantRepository).findBySchedule_IdOrderByPositionAsc(scheduleId);
    }

    @Test
    void getCoverageRejectsArchivedSchedule() {
        OnCallSchedule schedule = activeSchedule();
        schedule.setStatus(OnCallScheduleStatus.ARCHIVED);

        when(organizationAuthorizationService.requireMembership(organizationId, adminId)).thenReturn(adminMember);
        when(onCallScheduleRepository.findByIdAndOrganization_Id(scheduleId, organizationId))
                .thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> onCallScheduleService.getCoverage(organizationId, scheduleId, adminId, Instant.EPOCH, 1))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo("SCHEDULE_NOT_ACTIVE");
    }

    @Test
    void createRejectsInvalidTimezone() {
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId), eq(adminId), eq(OrganizationRole.OWNER), eq(OrganizationRole.ADMIN)))
                .thenReturn(adminMember);

        CreateOnCallScheduleParams params = new CreateOnCallScheduleParams(
                "Bad TZ",
                "Invalid/Zone",
                (short) 1,
                LocalTime.NOON,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                null,
                List.of(adminId));

        assertThatThrownBy(() -> onCallScheduleService.createSchedule(organizationId, params, adminId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void crossOrganizationScheduleLookupForbidden() {
        when(organizationAuthorizationService.requireMembership(organizationId, adminId)).thenReturn(adminMember);
        when(onCallScheduleRepository.findByIdAndOrganization_Id(scheduleId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> onCallScheduleService.getSchedule(organizationId, scheduleId, adminId))
                .isInstanceOf(ForbiddenException.class);
    }

    private OnCallSchedule activeSchedule() {
        OnCallSchedule schedule = OnCallSchedule.createDaily(
                organization, "Test", "UTC", LocalTime.NOON, LocalDate.of(2026, 1, 1), adminUser);
        org.springframework.test.util.ReflectionTestUtils.setField(schedule, "id", scheduleId);
        return schedule;
    }

    private static OnCallScheduleParticipant participantRow(OnCallSchedule schedule, UUID userId, int position) {
        User user = new User();
        user.setId(userId);
        user.setFirstName("P");
        user.setLastName("User");
        return OnCallScheduleParticipant.create(schedule, user, position);
    }
}
