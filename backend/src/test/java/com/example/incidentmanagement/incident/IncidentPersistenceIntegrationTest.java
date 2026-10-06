package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.service.Service;
import com.example.incidentmanagement.service.ServiceRepository;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class IncidentPersistenceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanAll();
    }

    @Test
    void persistsIncidentWithService() {
        User reporter = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "reporter@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("acme"));
        Service service = IncidentTestFixtures.createService(serviceRepository, organization, "payments");

        Incident incident = IncidentTestFixtures.newIncident(organization, 1L, reporter, "Outage", IncidentSeverity.SEV2);
        incident.setService(service);
        incident.setStatus(IncidentStatus.OPEN);

        Incident saved = incidentRepository.saveAndFlush(incident);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getService().getId()).isEqualTo(service.getId());
        assertThat(saved.getOrganization().getId()).isEqualTo(organization.getId());
        assertThat(saved.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(saved.getSeverity()).isEqualTo(IncidentSeverity.SEV2);
    }

    @Test
    void persistsIncidentWithoutService() {
        User reporter = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "noreporter@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("beta"));

        Incident incident =
                IncidentTestFixtures.newIncident(organization, 1L, reporter, "Org-wide", IncidentSeverity.SEV3);
        Incident saved = incidentRepository.saveAndFlush(incident);

        assertThat(saved.getService()).isNull();
        assertThat(saved.getCommander()).isNull();
    }

    @Test
    void defaultStatusIsOpenWhenNotSet() {
        User reporter = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "open@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("gamma"));

        Incident incident =
                IncidentTestFixtures.newIncident(organization, 1L, reporter, "Default status", IncidentSeverity.SEV4);
        assertThat(incident.getStatus()).isNull();

        Incident saved = incidentRepository.saveAndFlush(incident);
        assertThat(saved.getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    void milestoneTimestampsAreNullable() {
        User reporter = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "milestones@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("delta"));

        Incident incident = IncidentTestFixtures.newIncident(organization, 1L, reporter, "Timestamps", IncidentSeverity.SEV2);
        Incident saved = incidentRepository.saveAndFlush(incident);

        assertThat(saved.getAcknowledgedAt()).isNull();
        assertThat(saved.getResolvedAt()).isNull();
        assertThat(saved.getCancelledAt()).isNull();
    }

    @Test
    void reporterIsRequired() {
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("epsilon"));

        Incident incident = new Incident();
        incident.setOrganization(organization);
        incident.setIncidentNumber(1L);
        incident.setTitle("Missing reporter");
        incident.setSeverity(IncidentSeverity.SEV1);
        incident.setStatus(IncidentStatus.OPEN);

        assertThatThrownBy(() -> incidentRepository.saveAndFlush(incident))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void organizationScopedLookupIsolatesTenants() {
        User reporter = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "iso@example.com");
        Organization orgA =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("org-a"));
        Organization orgB =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("org-b"));

        Incident incident = IncidentTestFixtures.newIncident(orgA, 1L, reporter, "Tenant A", IncidentSeverity.SEV2);
        incident = incidentRepository.saveAndFlush(incident);

        assertThat(incidentRepository.findByIdAndOrganizationId(incident.getId(), orgA.getId()))
                .isPresent();
        assertThat(incidentRepository.findByIdAndOrganizationId(incident.getId(), orgB.getId()))
                .isEmpty();
    }

    @Test
    @Transactional
    void enforcesOrganizationScopedIncidentNumberUniqueness() {
        User reporter = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "unique@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("unique"));

        incidentRepository.saveAndFlush(
                IncidentTestFixtures.newIncident(organization, 1L, reporter, "First", IncidentSeverity.SEV2));
        entityManager.flush();
        entityManager.clear();

        Incident duplicate =
                IncidentTestFixtures.newIncident(organization, 1L, reporter, "Duplicate number", IncidentSeverity.SEV3);
        assertThatThrownBy(() -> {
                    incidentRepository.saveAndFlush(duplicate);
                    entityManager.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByOrganizationIdSupportsPagination() {
        User reporter = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "page@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("page"));

        incidentRepository.save(
                IncidentTestFixtures.newIncident(organization, 1L, reporter, "One", IncidentSeverity.SEV2));
        incidentRepository.save(
                IncidentTestFixtures.newIncident(organization, 2L, reporter, "Two", IncidentSeverity.SEV3));

        var page = incidentRepository.findByOrganizationId(
                organization.getId(), org.springframework.data.domain.PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }
}
