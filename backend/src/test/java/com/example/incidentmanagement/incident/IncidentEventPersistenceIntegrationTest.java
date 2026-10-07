package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class IncidentEventPersistenceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentEventRepository incidentEventRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanAll();
    }

    @Test
    void persistsIncidentEventWithPayload() {
        User actor = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "actor@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("evt"));
        Incident incident = persistIncident(organization, actor, 1L, "Outage");

        IncidentEvent event = IncidentTestFixtures.newEvent(
                organization,
                incident,
                actor,
                IncidentEventType.SEVERITY_CHANGED,
                Map.of("old", "SEV2", "new", "SEV1"));

        IncidentEvent saved = incidentEventRepository.saveAndFlush(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIncident().getId()).isEqualTo(incident.getId());
        assertThat(saved.getOrganization().getId()).isEqualTo(organization.getId());
        assertThat(saved.getActor().getId()).isEqualTo(actor.getId());
        assertThat(saved.getEventType()).isEqualTo(IncidentEventType.SEVERITY_CHANGED);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPayload()).containsEntry("old", "SEV2").containsEntry("new", "SEV1");
    }

    @Test
    void persistsRepresentativeEventTypes() {
        User actor = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "types@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("types"));
        Incident incident = persistIncident(organization, actor, 1L, "Types");

        incidentEventRepository.saveAndFlush(IncidentTestFixtures.newEvent(
                organization, incident, actor, IncidentEventType.INCIDENT_CREATED, Map.of()));
        incidentEventRepository.saveAndFlush(IncidentTestFixtures.newEvent(
                organization,
                incident,
                actor,
                IncidentEventType.STATUS_CHANGED,
                Map.of("old", "OPEN", "new", "ACKNOWLEDGED")));
        incidentEventRepository.saveAndFlush(IncidentTestFixtures.newEvent(
                organization,
                incident,
                actor,
                IncidentEventType.SEVERITY_CHANGED,
                Map.of("old", "SEV3", "new", "SEV2")));

        var page = incidentEventRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtDescIdDesc(
                organization.getId(), incident.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
                .extracting(IncidentEvent::getEventType)
                .containsExactly(
                        IncidentEventType.SEVERITY_CHANGED,
                        IncidentEventType.STATUS_CHANGED,
                        IncidentEventType.INCIDENT_CREATED);
    }

    @Test
    void rejectsNonexistentOrganization() {
        User actor = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "noorg@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("noorg"));
        Incident incident = persistIncident(organization, actor, 1L, "No org");

        UUID missingOrganizationId = UUID.randomUUID();
        Organization missing = organizationRepository.getReferenceById(missingOrganizationId);

        IncidentEvent event = IncidentTestFixtures.newEvent(
                missing, incident, actor, IncidentEventType.INCIDENT_CREATED, Map.of());

        assertThatThrownBy(() -> incidentEventRepository.saveAndFlush(event))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonexistentIncident() {
        User actor = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "noinc@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("noinc"));
        Incident incident = persistIncident(organization, actor, 1L, "Deleted");
        UUID deletedIncidentId = incident.getId();
        incidentRepository.delete(incident);
        incidentRepository.flush();

        Incident deletedReference = incidentRepository.getReferenceById(deletedIncidentId);
        IncidentEvent event = IncidentTestFixtures.newEvent(
                organization, deletedReference, actor, IncidentEventType.INCIDENT_CREATED, Map.of());

        assertThatThrownBy(() -> incidentEventRepository.saveAndFlush(event))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnsEventsNewestFirstWithDeterministicTieBreak() {
        User actor = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "order@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("order"));
        Incident incident = persistIncident(organization, actor, 1L, "Order");

        incidentEventRepository.saveAndFlush(IncidentTestFixtures.newEvent(
                organization, incident, actor, IncidentEventType.TITLE_CHANGED, Map.of("old", "a", "new", "b")));
        incidentEventRepository.saveAndFlush(IncidentTestFixtures.newEvent(
                organization, incident, actor, IncidentEventType.DESCRIPTION_CHANGED, Map.of("old", "x", "new", "y")));
        incidentEventRepository.saveAndFlush(IncidentTestFixtures.newEvent(
                organization,
                incident,
                actor,
                IncidentEventType.STATUS_CHANGED,
                Map.of("old", "OPEN", "new", "RESOLVED")));

        var page = incidentEventRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtDescIdDesc(
                organization.getId(), incident.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent()).isSortedAccordingTo(Comparator.comparing(
                        IncidentEvent::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(IncidentEvent::getId, Comparator.reverseOrder()));
    }

    @Test
    void organizationScopedQueryDoesNotLeakAcrossTenants() {
        User actor = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "tenant@example.com");
        Organization orgA =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("org-a"));
        Organization orgB =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("org-b"));
        Incident incidentA = persistIncident(orgA, actor, 1L, "A");
        Incident incidentB = persistIncident(orgB, actor, 1L, "B");

        incidentEventRepository.saveAndFlush(IncidentTestFixtures.newEvent(
                orgA, incidentA, actor, IncidentEventType.INCIDENT_CREATED, Map.of()));
        incidentEventRepository.saveAndFlush(IncidentTestFixtures.newEvent(
                orgB, incidentB, actor, IncidentEventType.INCIDENT_CREATED, Map.of()));

        var orgAPage = incidentEventRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtDescIdDesc(
                orgA.getId(), incidentA.getId(), PageRequest.of(0, 10));
        var crossTenantPage = incidentEventRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtDescIdDesc(
                orgA.getId(), incidentB.getId(), PageRequest.of(0, 10));

        assertThat(orgAPage.getTotalElements()).isEqualTo(1);
        assertThat(crossTenantPage.getTotalElements()).isZero();
    }

    @Test
    void repositoryExposesOnlyAppendFriendlyQueryMethods() {
        Method[] declared = IncidentEventRepository.class.getDeclaredMethods();
        assertThat(Arrays.stream(declared).map(Method::getName))
                .containsExactly("findByOrganization_IdAndIncident_IdOrderByCreatedAtDescIdDesc");
        assertThat(JpaRepository.class.isAssignableFrom(IncidentEventRepository.class)).isTrue();
    }

    private Incident persistIncident(Organization organization, User reporter, long number, String title) {
        return incidentRepository.saveAndFlush(
                IncidentTestFixtures.newIncident(organization, number, reporter, title, IncidentSeverity.SEV2));
    }
}
