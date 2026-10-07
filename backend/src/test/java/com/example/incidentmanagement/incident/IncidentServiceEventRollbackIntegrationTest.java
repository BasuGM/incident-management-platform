package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationMemberRepository;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class IncidentServiceEventRollbackIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentEventRepository incidentEventRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private IncidentEventRecorder incidentEventRecorder;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanAll();
        reset(incidentEventRecorder);
    }

    @Test
    void createRollsBackIncidentAndNumberWhenEventPersistenceFails() {
        var ctx = new OrgContext("create-rollback");
        doThrow(new DataIntegrityViolationException("event failed"))
                .doCallRealMethod()
                .when(incidentEventRecorder)
                .recordCreated(any(), any());

        assertThatThrownBy(() -> incidentService.createIncident(
                        ctx.organization.getId(),
                        ctx.member.getId(),
                        new CreateIncidentParams("Rollback", null, IncidentSeverity.SEV2, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(incidentRepository.count()).isZero();
        assertThat(incidentEventRepository.count()).isZero();

        Incident recovered = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Recovered", null, IncidentSeverity.SEV2, null, null));

        assertThat(recovered.getIncidentNumber()).isEqualTo(1L);
        assertThat(countCreatedEvents()).isEqualTo(1L);
    }

    @Test
    void updateRollsBackIncidentChangeWhenEventPersistenceFails() {
        var ctx = new OrgContext("update-rollback");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Stable", null, IncidentSeverity.SEV2, null, null));

        doThrow(new DataIntegrityViolationException("event failed"))
                .when(incidentEventRecorder)
                .recordSeverityChanged(any(), any(), any(), any());

        assertThatThrownBy(() -> incidentService.updateIncident(
                        ctx.organization.getId(),
                        incident.getId(),
                        ctx.admin.getId(),
                        IncidentUpdateSpec.builder().severity(IncidentSeverity.SEV1).build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        Incident reloaded = incidentService.getIncident(
                ctx.organization.getId(), incident.getId(), ctx.member.getId());
        assertThat(reloaded.getSeverity()).isEqualTo(IncidentSeverity.SEV2);
        assertThat(countSeverityChangedEvents(incident)).isZero();
    }

    private long countCreatedEvents() {
        return incidentEventRepository.findAll().stream()
                .filter(e -> e.getEventType() == IncidentEventType.INCIDENT_CREATED)
                .count();
    }

    private long countSeverityChangedEvents(Incident incident) {
        return incidentEventRepository.findAll().stream()
                .filter(e -> e.getIncident().getId().equals(incident.getId()))
                .filter(e -> e.getEventType() == IncidentEventType.SEVERITY_CHANGED)
                .count();
    }

    private final class OrgContext {
        final Organization organization;
        final User member;
        final User admin;

        OrgContext(String slugPrefix) {
            member = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-member@example.com");
            admin = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-admin@example.com");
            organization = IncidentTestFixtures.createOrganization(
                    organizationRepository, IncidentTestFixtures.uniqueSlug(slugPrefix));
            addMember(member, OrganizationRole.MEMBER);
            addMember(admin, OrganizationRole.ADMIN);
        }

        private void addMember(User user, OrganizationRole role) {
            OrganizationMember organizationMember = new OrganizationMember();
            organizationMember.setOrganization(organization);
            organizationMember.setUser(user);
            organizationMember.setRole(role);
            organizationMemberRepository.save(organizationMember);
        }
    }
}
