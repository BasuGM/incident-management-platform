package com.example.incidentmanagement.oncall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.team.Team;
import com.example.incidentmanagement.team.TeamRepository;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import com.example.incidentmanagement.user.UserRole;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class OnCallScheduleMigrationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM app.on_call_schedule_participants");
        jdbcTemplate.update("DELETE FROM app.on_call_schedules");
        databaseCleaner.cleanAll();
    }

    @Test
    void flywayMigrationV10AppliedSuccessfully() {
        MigrationInfo v10 = Arrays.stream(flyway.info().all())
                .filter(info -> info.getVersion() != null && "10".equals(info.getVersion().toString()))
                .findFirst()
                .orElseThrow();

        assertThat(v10.getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'app' AND table_name = 'on_call_schedules'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'app' AND table_name = 'on_call_schedule_participants'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void persistsScheduleAndParticipants() {
        Fixture fx = newFixture();
        UUID scheduleId = UUID.randomUUID();

        insertWeeklySchedule(scheduleId, fx, null, null);

        UUID participantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app.on_call_schedule_participants (id, schedule_id, user_id, position) VALUES (?, ?, ?, ?)",
                participantId,
                scheduleId,
                fx.user.getId(),
                0);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT name FROM app.on_call_schedules WHERE id = ?", String.class, scheduleId))
                .isEqualTo("Primary");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM app.on_call_schedule_participants WHERE schedule_id = ?",
                        Integer.class,
                        scheduleId))
                .isEqualTo(1);
    }

    @Test
    void rejectsBothTeamAndServiceScope() {
        Fixture fx = newFixture();
        Team team = persistTeam(fx.organization, "Platform");
        UUID serviceId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO app.services (id, organization_id, name, slug) VALUES (?, ?, ?, ?)",
                serviceId,
                fx.organization.getId(),
                "Payments",
                "payments");

        UUID scheduleId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO app.on_call_schedules ("
                                + "id, organization_id, name, status, timezone, rotation_length_days, "
                                + "handoff_local_time, handoff_day_of_week, effective_from, team_id, service_id, "
                                + "created_by_id, updated_by_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        scheduleId,
                        fx.organization.getId(),
                        "Invalid",
                        "ACTIVE",
                        "UTC",
                        7,
                        LocalTime.of(9, 0),
                        1,
                        LocalDate.of(2026, 1, 1),
                        team.getId(),
                        serviceId,
                        fx.user.getId(),
                        fx.user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsWeeklyRotationWithoutDayOfWeek() {
        Fixture fx = newFixture();
        UUID scheduleId = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO app.on_call_schedules ("
                                + "id, organization_id, name, status, timezone, rotation_length_days, "
                                + "handoff_local_time, handoff_day_of_week, effective_from, "
                                + "created_by_id, updated_by_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        scheduleId,
                        fx.organization.getId(),
                        "Weekly bad",
                        "ACTIVE",
                        "UTC",
                        7,
                        LocalTime.of(9, 0),
                        null,
                        LocalDate.of(2026, 1, 1),
                        fx.user.getId(),
                        fx.user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDailyRotationWithDayOfWeek() {
        Fixture fx = newFixture();
        UUID scheduleId = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO app.on_call_schedules ("
                                + "id, organization_id, name, status, timezone, rotation_length_days, "
                                + "handoff_local_time, handoff_day_of_week, effective_from, "
                                + "created_by_id, updated_by_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        scheduleId,
                        fx.organization.getId(),
                        "Daily bad",
                        "ACTIVE",
                        "UTC",
                        1,
                        LocalTime.of(9, 0),
                        1,
                        LocalDate.of(2026, 1, 1),
                        fx.user.getId(),
                        fx.user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateParticipantUser() {
        Fixture fx = newFixture();
        UUID scheduleId = UUID.randomUUID();
        insertWeeklySchedule(scheduleId, fx, null, null);

        jdbcTemplate.update(
                "INSERT INTO app.on_call_schedule_participants (id, schedule_id, user_id, position) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                scheduleId,
                fx.user.getId(),
                0);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO app.on_call_schedule_participants (id, schedule_id, user_id, position) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(),
                        scheduleId,
                        fx.user.getId(),
                        1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateParticipantPosition() {
        Fixture fx = newFixture();
        User other = createUser("other@example.com");
        UUID scheduleId = UUID.randomUUID();
        insertWeeklySchedule(scheduleId, fx, null, null);

        jdbcTemplate.update(
                "INSERT INTO app.on_call_schedule_participants (id, schedule_id, user_id, position) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                scheduleId,
                fx.user.getId(),
                0);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO app.on_call_schedule_participants (id, schedule_id, user_id, position) VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(),
                        scheduleId,
                        other.getId(),
                        0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDeletingTeamReferencedBySchedule() {
        Fixture fx = newFixture();
        Team team = persistTeam(fx.organization, "On-call team");
        UUID scheduleId = UUID.randomUUID();
        insertWeeklySchedule(scheduleId, fx, team.getId(), null);

        assertThatThrownBy(() -> teamRepository.delete(team)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingScheduleCascadesToParticipants() {
        Fixture fx = newFixture();
        UUID scheduleId = UUID.randomUUID();
        insertWeeklySchedule(scheduleId, fx, null, null);
        jdbcTemplate.update(
                "INSERT INTO app.on_call_schedule_participants (id, schedule_id, user_id, position) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                scheduleId,
                fx.user.getId(),
                0);

        jdbcTemplate.update("DELETE FROM app.on_call_schedules WHERE id = ?", scheduleId);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM app.on_call_schedule_participants WHERE schedule_id = ?",
                        Integer.class,
                        scheduleId))
                .isZero();
    }

    private void insertWeeklySchedule(UUID scheduleId, Fixture fx, UUID teamId, UUID serviceId) {
        jdbcTemplate.update(
                "INSERT INTO app.on_call_schedules ("
                        + "id, organization_id, name, status, timezone, rotation_length_days, "
                        + "handoff_local_time, handoff_day_of_week, effective_from, team_id, service_id, "
                        + "created_by_id, updated_by_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scheduleId,
                fx.organization.getId(),
                "Primary",
                "ACTIVE",
                "America/New_York",
                7,
                LocalTime.of(9, 0),
                1,
                LocalDate.of(2026, 1, 6),
                teamId,
                serviceId,
                fx.user.getId(),
                fx.user.getId());
    }

    private Fixture newFixture() {
        User user = createUser("oncall@example.com");
        Organization organization = createOrganization("oncall-" + UUID.randomUUID().toString().substring(0, 8));
        return new Fixture(user, organization);
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

    private Team persistTeam(Organization organization, String name) {
        Team team = new Team();
        team.setOrganization(organization);
        team.setName(name);
        return teamRepository.saveAndFlush(team);
    }

    private record Fixture(User user, Organization organization) {}
}
