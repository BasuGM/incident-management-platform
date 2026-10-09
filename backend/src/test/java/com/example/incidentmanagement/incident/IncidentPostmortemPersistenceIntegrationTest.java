package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import java.time.Instant;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class IncidentPostmortemPersistenceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentPostmortemRepository incidentPostmortemRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

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
        databaseCleaner.cleanAll();
    }

    @Test
    void flywayMigrationV9AppliedSuccessfully() {
        MigrationInfo v9 = Arrays.stream(flyway.info().all())
                .filter(info -> info.getVersion() != null && "9".equals(info.getVersion().toString()))
                .findFirst()
                .orElseThrow();

        assertThat(v9.getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'app' AND table_name = 'incident_postmortems'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void persistsPostmortemWithRelationshipsAndTimestamps() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "author@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("pm"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentPostmortem postmortem =
                IncidentTestFixtures.newPostmortem(organization, incident, author, "Postmortem: Outage");
        postmortem.updateSummary("Summary text");
        postmortem.updateRootCause("Config drift");

        IncidentPostmortem saved = incidentPostmortemRepository.saveAndFlush(postmortem);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrganization().getId()).isEqualTo(organization.getId());
        assertThat(saved.getIncident().getId()).isEqualTo(incident.getId());
        assertThat(saved.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(saved.getStatus()).isEqualTo(IncidentPostmortemStatus.DRAFT);
        assertThat(saved.getTitle()).isEqualTo("Postmortem: Outage");
        assertThat(saved.getSummary()).isEqualTo("Summary text");
        assertThat(saved.getRootCause()).isEqualTo("Config drift");
        assertThat(saved.getImpact()).isEmpty();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getPublishedBy()).isNull();
        assertThat(saved.getArchivedAt()).isNull();
    }

    @Test
    void enforcesOnePostmortemPerIncident() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "uniq@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("uniq"));
        Incident incident = persistIncident(organization, author, 1L);

        incidentPostmortemRepository.saveAndFlush(
                IncidentTestFixtures.newPostmortem(organization, incident, author, "First"));

        IncidentPostmortem duplicate =
                IncidentTestFixtures.newPostmortem(organization, incident, author, "Second");

        assertThatThrownBy(() -> incidentPostmortemRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lifecycleFieldsRoundTrip() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "life@example.com");
        User publisher = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "pub@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("life"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentPostmortem postmortem =
                IncidentTestFixtures.newPostmortem(organization, incident, author, "Lifecycle");
        Instant publishedAt = Instant.parse("2026-03-01T12:00:00Z");
        Instant archivedAt = Instant.parse("2026-03-02T08:00:00Z");
        postmortem.assignStatus(IncidentPostmortemStatus.PUBLISHED);
        postmortem.assignPublicationMetadata(publishedAt, publisher);
        postmortem.assignArchivedAt(archivedAt);

        UUID id = incidentPostmortemRepository.saveAndFlush(postmortem).getId();

        IncidentPostmortem reloaded = incidentPostmortemRepository
                .findByIdAndOrganization_IdAndIncident_Id(id, organization.getId(), incident.getId())
                .orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(IncidentPostmortemStatus.PUBLISHED);
        assertThat(reloaded.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(reloaded.getPublishedBy().getId()).isEqualTo(publisher.getId());
        assertThat(reloaded.getArchivedAt()).isEqualTo(archivedAt);
    }

    @Test
    void rejectsTitleLongerThan200Characters() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "title@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("title"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentPostmortem postmortem =
                IncidentTestFixtures.newPostmortem(organization, incident, author, "ok");
        postmortem.updateTitle("a".repeat(201));

        assertThatThrownBy(() -> incidentPostmortemRepository.saveAndFlush(postmortem))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSummaryLongerThan10000Characters() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "sum@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("sum"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentPostmortem postmortem =
                IncidentTestFixtures.newPostmortem(organization, incident, author, "ok");
        postmortem.updateSummary("x".repeat(10001));

        assertThatThrownBy(() -> incidentPostmortemRepository.saveAndFlush(postmortem))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidStatusCheckConstraint() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "status@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("status"));
        Incident incident = persistIncident(organization, author, 1L);
        IncidentPostmortem saved = incidentPostmortemRepository.saveAndFlush(
                IncidentTestFixtures.newPostmortem(organization, incident, author, "Status"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE app.incident_postmortems SET status = ? WHERE id = ?",
                        "INVALID",
                        saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingIncidentCascadesToPostmortem() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "casc@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("casc"));
        Incident incident = persistIncident(organization, author, 1L);
        UUID incidentId = incident.getId();

        incidentPostmortemRepository.saveAndFlush(
                IncidentTestFixtures.newPostmortem(organization, incident, author, "Gone with incident"));

        incidentRepository.delete(incident);
        incidentRepository.flush();

        assertThat(incidentPostmortemRepository.findByOrganization_IdAndIncident_Id(
                        organization.getId(), incidentId))
                .isEmpty();
    }

    @Test
    void deletingOrganizationCascadesToPostmortem() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "orgc@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("orgc"));
        Incident incident = persistIncident(organization, author, 1L);
        UUID organizationId = organization.getId();
        UUID incidentId = incident.getId();

        incidentPostmortemRepository.saveAndFlush(
                IncidentTestFixtures.newPostmortem(organization, incident, author, "Org cascade"));

        organizationRepository.delete(organization);
        organizationRepository.flush();

        assertThat(incidentPostmortemRepository.findByOrganization_IdAndIncident_Id(organizationId, incidentId))
                .isEmpty();
    }

    @Test
    void rejectsDeletingAuthorReferencedByPostmortem() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "authdel@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(
                        organizationRepository, IncidentTestFixtures.uniqueSlug("authdel"));
        Incident incident = persistIncident(organization, author, 1L);

        incidentPostmortemRepository.saveAndFlush(
                IncidentTestFixtures.newPostmortem(organization, incident, author, "Author lock"));

        assertThatThrownBy(() -> userRepository.delete(author))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDeletingPublisherReferencedByPostmortem() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "auth2@example.com");
        User publisher = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "pubdel@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("pubdel"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentPostmortem postmortem =
                IncidentTestFixtures.newPostmortem(organization, incident, author, "Publisher lock");
        postmortem.assignPublicationMetadata(Instant.parse("2026-01-01T00:00:00Z"), publisher);
        incidentPostmortemRepository.saveAndFlush(postmortem);

        assertThatThrownBy(() -> userRepository.delete(publisher))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void organizationScopedQueriesIsolateTenants() {
        User authorA = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "a@example.com");
        User authorB = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "b@example.com");
        Organization orgA =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("pm-a"));
        Organization orgB =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("pm-b"));
        Incident incidentA = persistIncident(orgA, authorA, 1L);
        Incident incidentB = persistIncident(orgB, authorB, 1L);

        incidentPostmortemRepository.saveAndFlush(
                IncidentTestFixtures.newPostmortem(orgA, incidentA, authorA, "A"));
        incidentPostmortemRepository.saveAndFlush(
                IncidentTestFixtures.newPostmortem(orgB, incidentB, authorB, "B"));

        assertThat(incidentPostmortemRepository
                        .findByOrganization_IdAndIncident_Id(orgA.getId(), incidentA.getId())
                        .orElseThrow()
                        .getTitle())
                .isEqualTo("A");
        assertThat(incidentPostmortemRepository.findByOrganization_IdAndIncident_Id(orgA.getId(), incidentB.getId()))
                .isEmpty();

        var pageA = incidentPostmortemRepository.findByOrganization_IdOrderByCreatedAtDescIdDesc(
                orgA.getId(), PageRequest.of(0, 10));
        var pageB = incidentPostmortemRepository.findByOrganization_IdOrderByCreatedAtDescIdDesc(
                orgB.getId(), PageRequest.of(0, 10));

        assertThat(pageA.getTotalElements()).isEqualTo(1);
        assertThat(pageB.getTotalElements()).isEqualTo(1);
        assertThat(pageA.getContent().getFirst().getTitle()).isEqualTo("A");
        assertThat(pageB.getContent().getFirst().getTitle()).isEqualTo("B");
    }

    @Test
    void listsPublishedPostmortemsByOrganizationAndStatus() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "list@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("list"));
        Incident incidentDraft = persistIncident(organization, author, 1L);
        Incident incidentPublished = persistIncident(organization, author, 2L);

        incidentPostmortemRepository.saveAndFlush(
                IncidentTestFixtures.newPostmortem(organization, incidentDraft, author, "Draft only"));

        IncidentPostmortem published =
                IncidentTestFixtures.newPostmortem(organization, incidentPublished, author, "Published");
        published.assignStatus(IncidentPostmortemStatus.PUBLISHED);
        published.assignPublicationMetadata(Instant.parse("2026-02-10T10:00:00Z"), author);
        incidentPostmortemRepository.saveAndFlush(published);

        var publishedPage = incidentPostmortemRepository
                .findByOrganization_IdAndStatusOrderByPublishedAtDescCreatedAtDescIdDesc(
                        organization.getId(), IncidentPostmortemStatus.PUBLISHED, PageRequest.of(0, 10));
        var draftPage = incidentPostmortemRepository
                .findByOrganization_IdAndStatusOrderByPublishedAtDescCreatedAtDescIdDesc(
                        organization.getId(), IncidentPostmortemStatus.DRAFT, PageRequest.of(0, 10));

        assertThat(publishedPage.getTotalElements()).isEqualTo(1);
        assertThat(publishedPage.getContent().getFirst().getTitle()).isEqualTo("Published");
        assertThat(draftPage.getTotalElements()).isEqualTo(1);
        assertThat(draftPage.getContent().getFirst().getTitle()).isEqualTo("Draft only");
    }

    private Incident persistIncident(Organization organization, User reporter, long number) {
        return incidentRepository.saveAndFlush(IncidentTestFixtures.newIncident(
                organization, number, reporter, "Incident " + number, IncidentSeverity.SEV2));
    }
}
