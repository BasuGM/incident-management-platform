package com.example.incidentmanagement.oncall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationMemberRepository;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.team.Team;
import com.example.incidentmanagement.team.TeamMember;
import com.example.incidentmanagement.team.TeamMemberRepository;
import com.example.incidentmanagement.team.TeamRepository;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import com.example.incidentmanagement.user.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class OnCallScheduleServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private OnCallScheduleService onCallScheduleService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private OrgFixture fx;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanAll();
        fx = new OrgFixture();
    }

    @Test
    void adminCreatesScheduleAndMemberReadsCoverage() {
        CreateOnCallScheduleParams params = new CreateOnCallScheduleParams(
                "Primary",
                "UTC",
                (short) 1,
                LocalTime.of(9, 0),
                null,
                LocalDate.of(2026, 1, 1),
                null,
                null,
                List.of(fx.member.getId(), fx.owner.getId()));

        OnCallScheduleDetail created = onCallScheduleService.createSchedule(fx.organization.getId(), params, fx.admin.getId());

        assertThat(created.participants()).hasSize(2);
        assertThat(created.summary().status()).isEqualTo(OnCallScheduleStatus.ACTIVE);

        OnCallCoverageResult coverage = onCallScheduleService.getCoverage(
                fx.organization.getId(),
                created.summary().id(),
                fx.member.getId(),
                Instant.parse("2026-01-01T09:00:00Z"),
                2);

        assertThat(coverage.currentShift()).isNotNull();
        assertThat(coverage.currentShift().participantUserId()).isEqualTo(fx.member.getId());
        assertThat(coverage.upcomingShifts()).hasSize(2);
    }

    @Test
    void viewerCannotCreateSchedule() {
        CreateOnCallScheduleParams params = dailyParams("Blocked", List.of(fx.member.getId()));

        assertThatThrownBy(() -> onCallScheduleService.createSchedule(fx.organization.getId(), params, fx.viewer.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nonMemberDenied() {
        User outsider = createUser("outsider@example.com");

        assertThatThrownBy(() -> onCallScheduleService.listSchedules(
                        fx.organization.getId(), outsider.getId(), OnCallScheduleListFilter.ACTIVE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void teamScopedScheduleRejectsNonTeamParticipant() {
        Team team = persistTeam("Platform");
        addTeamMember(team, fx.owner);

        CreateOnCallScheduleParams params = new CreateOnCallScheduleParams(
                "Team scope",
                "UTC",
                (short) 1,
                LocalTime.NOON,
                null,
                LocalDate.of(2026, 1, 1),
                team.getId(),
                null,
                List.of(fx.member.getId()));

        assertThatThrownBy(() -> onCallScheduleService.createSchedule(fx.organization.getId(), params, fx.admin.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("team");
    }

    @Test
    void updateReplacesParticipantsAtomically() {
        OnCallScheduleDetail created = onCallScheduleService.createSchedule(
                fx.organization.getId(), dailyParams("Swap", List.of(fx.member.getId())), fx.admin.getId());

        OnCallScheduleDetail updated = onCallScheduleService.updateSchedule(
                fx.organization.getId(),
                created.summary().id(),
                new UpdateOnCallScheduleParams(
                        null, null, null, null, null, null, null, null, null, List.of(fx.owner.getId(), fx.admin.getId())),
                fx.admin.getId());

        assertThat(updated.participants()).extracting(OnCallScheduleParticipantView::userId)
                .containsExactly(fx.owner.getId(), fx.admin.getId());
    }

    @Test
    void archivedScheduleRejectsCoverageAndUpdate() {
        OnCallScheduleDetail created = onCallScheduleService.createSchedule(
                fx.organization.getId(), dailyParams("Archive me", List.of(fx.member.getId())), fx.admin.getId());

        onCallScheduleService.archiveSchedule(fx.organization.getId(), created.summary().id(), fx.admin.getId());

        assertThatThrownBy(() -> onCallScheduleService.getCoverage(
                        fx.organization.getId(), created.summary().id(), fx.member.getId(), Instant.EPOCH, 1))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo("SCHEDULE_NOT_ACTIVE");

        assertThatThrownBy(() -> onCallScheduleService.updateSchedule(
                        fx.organization.getId(),
                        created.summary().id(),
                        new UpdateOnCallScheduleParams("New", null, null, null, null, null, null, null, null, null),
                        fx.admin.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void duplicateNameRejected() {
        onCallScheduleService.createSchedule(
                fx.organization.getId(), dailyParams("Dup", List.of(fx.member.getId())), fx.admin.getId());

        assertThatThrownBy(() -> onCallScheduleService.createSchedule(
                        fx.organization.getId(), dailyParams("dup", List.of(fx.member.getId())), fx.admin.getId()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo("DUPLICATE_NAME");
    }

    private CreateOnCallScheduleParams dailyParams(String name, List<UUID> participants) {
        return new CreateOnCallScheduleParams(
                name, "UTC", (short) 1, LocalTime.NOON, null, LocalDate.of(2026, 1, 1), null, null, participants);
    }

    private Team persistTeam(String name) {
        Team team = new Team();
        team.setOrganization(fx.organization);
        team.setName(name);
        return teamRepository.saveAndFlush(team);
    }

    private void addTeamMember(Team team, User user) {
        TeamMember teamMember = new TeamMember();
        teamMember.setTeam(team);
        teamMember.setUser(user);
        teamMemberRepository.saveAndFlush(teamMember);
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(UserRole.ENGINEER);
        return userRepository.saveAndFlush(user);
    }

    private Organization createOrganization(String slug) {
        Organization organization = new Organization();
        organization.setName("Org " + slug);
        organization.setSlug(slug);
        return organizationRepository.saveAndFlush(organization);
    }

    private final class OrgFixture {
        final User owner;
        final User admin;
        final User member;
        final User viewer;
        final Organization organization;

        OrgFixture() {
            String slug = "oncall-svc-" + UUID.randomUUID().toString().substring(0, 8);
            owner = createUser(slug + "-owner@example.com");
            admin = createUser(slug + "-admin@example.com");
            member = createUser(slug + "-member@example.com");
            viewer = createUser(slug + "-viewer@example.com");
            organization = createOrganization(slug);
            addOrgMember(owner, OrganizationRole.OWNER);
            addOrgMember(admin, OrganizationRole.ADMIN);
            addOrgMember(member, OrganizationRole.MEMBER);
            addOrgMember(viewer, OrganizationRole.VIEWER);
        }

        private void addOrgMember(User user, OrganizationRole role) {
            OrganizationMember organizationMember = new OrganizationMember();
            organizationMember.setOrganization(organization);
            organizationMember.setUser(user);
            organizationMember.setRole(role);
            organizationMemberRepository.saveAndFlush(organizationMember);
        }
    }
}
