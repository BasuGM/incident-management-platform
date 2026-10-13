package com.example.incidentmanagement.oncall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.service.Service;
import com.example.incidentmanagement.service.ServiceRepository;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.team.Team;
import com.example.incidentmanagement.team.TeamRepository;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import com.example.incidentmanagement.user.UserRole;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class OnCallSchedulePersistenceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private OnCallScheduleRepository onCallScheduleRepository;

    @Autowired
    private OnCallScheduleParticipantRepository onCallScheduleParticipantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanAll();
    }

    @Test
    void persistsWeeklyScheduleWithScopeAndTimestamps() {
        Fixture fx = newFixture();
        Team team = persistTeam(fx.organization, "Platform");
        OnCallSchedule schedule = OnCallSchedule.createWeekly(
                fx.organization,
                "Payments",
                "America/New_York",
                LocalTime.of(9, 0),
                (short) 1,
                LocalDate.of(2026, 1, 6),
                fx.actor);
        schedule.setTeam(team);

        OnCallSchedule saved = onCallScheduleRepository.saveAndFlush(schedule);

        OnCallSchedule reloaded = onCallScheduleRepository
                .findByIdAndOrganization_Id(saved.getId(), fx.organization.getId())
                .orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("Payments");
        assertThat(reloaded.getStatus()).isEqualTo(OnCallScheduleStatus.ACTIVE);
        assertThat(reloaded.getTimezone()).isEqualTo("America/New_York");
        assertThat(reloaded.getRotationLengthDays()).isEqualTo((short) 7);
        assertThat(reloaded.getHandoffLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(reloaded.getHandoffDayOfWeek()).isEqualTo((short) 1);
        assertThat(reloaded.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 6));
        assertThat(reloaded.getTeam().getId()).isEqualTo(team.getId());
        assertThat(reloaded.getService()).isNull();
        assertThat(reloaded.getCreatedBy().getId()).isEqualTo(fx.actor.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void persistsDailyScheduleWithNullHandoffDayOfWeek() {
        Fixture fx = newFixture();
        Service service = persistService(fx.organization, "api");

        OnCallSchedule schedule = OnCallSchedule.createDaily(
                fx.organization,
                "Daily rotation",
                "UTC",
                LocalTime.of(17, 30),
                LocalDate.of(2026, 3, 1),
                fx.actor);
        schedule.setService(service);

        UUID id = onCallScheduleRepository.saveAndFlush(schedule).getId();

        OnCallSchedule reloaded = onCallScheduleRepository
                .findByIdAndOrganization_Id(id, fx.organization.getId())
                .orElseThrow();

        assertThat(reloaded.getRotationLengthDays()).isEqualTo((short) 1);
        assertThat(reloaded.getHandoffDayOfWeek()).isNull();
        assertThat(reloaded.getService().getId()).isEqualTo(service.getId());
    }

    @Test
    void archiveStatusRoundTripsWithoutDeletingRow() {
        Fixture fx = newFixture();
        OnCallSchedule schedule = OnCallSchedule.createWeekly(
                fx.organization,
                "Archive me",
                "UTC",
                LocalTime.of(8, 0),
                (short) 2,
                LocalDate.of(2026, 1, 1),
                fx.actor);
        UUID id = onCallScheduleRepository.saveAndFlush(schedule).getId();

        schedule.setStatus(OnCallScheduleStatus.ARCHIVED);
        onCallScheduleRepository.saveAndFlush(schedule);

        assertThat(onCallScheduleRepository.findById(id)).isPresent();
        assertThat(onCallScheduleRepository
                        .findByIdAndOrganization_Id(id, fx.organization.getId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(OnCallScheduleStatus.ARCHIVED);
    }

    @Test
    void participantsPersistInPositionOrder() {
        Fixture fx = newFixture();
        User second = createUser("second@example.com");
        OnCallSchedule schedule = onCallScheduleRepository.saveAndFlush(OnCallSchedule.createWeekly(
                fx.organization,
                "Rotation",
                "UTC",
                LocalTime.of(9, 0),
                (short) 1,
                LocalDate.of(2026, 1, 1),
                fx.actor));

        onCallScheduleParticipantRepository.saveAndFlush(
                OnCallScheduleParticipant.create(schedule, second, 1));
        onCallScheduleParticipantRepository.saveAndFlush(
                OnCallScheduleParticipant.create(schedule, fx.actor, 0));

        List<OnCallScheduleParticipant> ordered =
                onCallScheduleParticipantRepository.findBySchedule_IdOrderByPositionAsc(schedule.getId());

        assertThat(ordered).hasSize(2);
        assertThat(ordered.get(0).getUser().getId()).isEqualTo(fx.actor.getId());
        assertThat(ordered.get(0).getPosition()).isZero();
        assertThat(ordered.get(1).getUser().getId()).isEqualTo(second.getId());
        assertThat(ordered.get(1).getPosition()).isEqualTo(1);
    }

    @Test
    void organizationScopedQueriesIsolateTenants() {
        Fixture fxA = newFixture();
        Fixture fxB = newFixture();
        onCallScheduleRepository.saveAndFlush(OnCallSchedule.createWeekly(
                fxA.organization, "A", "UTC", LocalTime.NOON, (short) 1, LocalDate.of(2026, 1, 1), fxA.actor));
        onCallScheduleRepository.saveAndFlush(OnCallSchedule.createWeekly(
                fxB.organization, "B", "UTC", LocalTime.NOON, (short) 1, LocalDate.of(2026, 1, 1), fxB.actor));

        assertThat(onCallScheduleRepository.findByOrganization_IdOrderByNameAsc(fxA.organization.getId()))
                .hasSize(1)
                .first()
                .extracting(OnCallSchedule::getName)
                .isEqualTo("A");

        UUID scheduleBId = onCallScheduleRepository
                .findByOrganization_IdOrderByNameAsc(fxB.organization.getId())
                .getFirst()
                .getId();
        assertThat(onCallScheduleRepository.findByIdAndOrganization_Id(scheduleBId, fxA.organization.getId()))
                .isEmpty();
    }

    @Test
    void filtersByStatusWithinOrganization() {
        Fixture fx = newFixture();
        OnCallSchedule active = OnCallSchedule.createWeekly(
                fx.organization, "Active", "UTC", LocalTime.NOON, (short) 1, LocalDate.of(2026, 1, 1), fx.actor);
        OnCallSchedule archived = OnCallSchedule.createWeekly(
                fx.organization, "Archived", "UTC", LocalTime.NOON, (short) 1, LocalDate.of(2026, 1, 1), fx.actor);
        archived.setStatus(OnCallScheduleStatus.ARCHIVED);
        onCallScheduleRepository.saveAllAndFlush(List.of(active, archived));

        assertThat(onCallScheduleRepository.findByOrganization_IdAndStatusOrderByNameAsc(
                        fx.organization.getId(), OnCallScheduleStatus.ACTIVE))
                .extracting(OnCallSchedule::getName)
                .containsExactly("Active");
    }

    @Test
    void existsByNameIgnoreCaseWithinOrganization() {
        Fixture fx = newFixture();
        onCallScheduleRepository.saveAndFlush(OnCallSchedule.createWeekly(
                fx.organization, "Primary", "UTC", LocalTime.NOON, (short) 1, LocalDate.of(2026, 1, 1), fx.actor));

        assertThat(onCallScheduleRepository.existsByOrganization_IdAndNameIgnoreCase(
                        fx.organization.getId(), "primary"))
                .isTrue();
        assertThat(onCallScheduleRepository.existsByOrganization_IdAndNameIgnoreCase(
                        fx.organization.getId(), "other"))
                .isFalse();
    }

    @Test
    void enforcesUniqueOrganizationName() {
        Fixture fx = newFixture();
        onCallScheduleRepository.saveAndFlush(OnCallSchedule.createWeekly(
                fx.organization, "Dup", "UTC", LocalTime.NOON, (short) 1, LocalDate.of(2026, 1, 1), fx.actor));

        OnCallSchedule duplicate = OnCallSchedule.createWeekly(
                fx.organization, "Dup", "UTC", LocalTime.NOON, (short) 1, LocalDate.of(2026, 1, 2), fx.actor);

        assertThatThrownBy(() -> onCallScheduleRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture newFixture() {
        User actor = createUser("actor-" + UUID.randomUUID() + "@example.com");
        Organization organization = createOrganization("org-" + UUID.randomUUID().toString().substring(0, 8));
        return new Fixture(actor, organization);
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

    private Service persistService(Organization organization, String slug) {
        Service service = new Service();
        service.setOrganization(organization);
        service.setName(slug);
        service.setSlug(slug);
        return serviceRepository.saveAndFlush(service);
    }

    private record Fixture(User actor, Organization organization) {}
}
