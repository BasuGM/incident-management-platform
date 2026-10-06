package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationMemberRepository;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.service.Service;
import com.example.incidentmanagement.service.ServiceRepository;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class IncidentServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentNumberAllocator incidentNumberAllocator;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanAll();
    }

    @Test
    void createWithoutServiceAndWithService() {
        var ctx = new OrgContext("create");
        Service service = IncidentTestFixtures.createService(serviceRepository, ctx.organization, "api");

        Incident noService = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Org-wide", null, IncidentSeverity.SEV3, null, null));
        Incident withService = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("API", "desc", IncidentSeverity.SEV2, service.getId(), null));

        assertThat(noService.getService()).isNull();
        assertThat(noService.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(noService.getIncidentNumber()).isEqualTo(1L);
        assertThat(withService.getService().getId()).isEqualTo(service.getId());
        assertThat(withService.getIncidentNumber()).isEqualTo(2L);
    }

    @Test
    void rejectsCrossOrgService() {
        var ctxA = new OrgContext("org-a");
        var ctxB = new OrgContext("org-b");
        Service serviceB = IncidentTestFixtures.createService(serviceRepository, ctxB.organization, "other");

        assertThatThrownBy(() -> incidentService.createIncident(
                        ctxA.organization.getId(),
                        ctxA.member.getId(),
                        new CreateIncidentParams("Bad", null, IncidentSeverity.SEV2, serviceB.getId(), null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsCrossOrgCommander() {
        var ctxA = new OrgContext("cmd-a");
        var ctxB = new OrgContext("cmd-b");

        assertThatThrownBy(() -> incidentService.createIncident(
                        ctxA.organization.getId(),
                        ctxA.member.getId(),
                        new CreateIncidentParams("Bad", null, IncidentSeverity.SEV2, null, ctxB.member.getId())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void organizationScopedRetrievalAndPagination() {
        var ctx = new OrgContext("page");
        incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("One", null, IncidentSeverity.SEV4, null, null));
        incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Two", null, IncidentSeverity.SEV3, null, null));

        var page = incidentService.listIncidents(
                ctx.organization.getId(),
                ctx.viewer.getId(),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);

        Incident first = incidentService.getIncident(
                ctx.organization.getId(), page.getContent().getFirst().getId(), ctx.viewer.getId());
        assertThat(first.getTitle()).isNotBlank();
    }

    @Test
    void crossOrgRetrievalForbidden() {
        var ctxA = new OrgContext("get-a");
        var ctxB = new OrgContext("get-b");
        Incident incident = incidentService.createIncident(
                ctxA.organization.getId(),
                ctxA.member.getId(),
                new CreateIncidentParams("Private", null, IncidentSeverity.SEV2, null, null));

        assertThatThrownBy(() -> incidentService.getIncident(
                        ctxB.organization.getId(), incident.getId(), ctxB.member.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void statusTransitionsAndMilestones() {
        var ctx = new OrgContext("status");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Status", null, IncidentSeverity.SEV2, null, null));

        Incident acknowledged = incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.ACKNOWLEDGED).build());
        assertThat(acknowledged.getAcknowledgedAt()).isNotNull();

        Incident resolved = incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.RESOLVED).build());
        assertThat(resolved.getAcknowledgedAt()).isEqualTo(acknowledged.getAcknowledgedAt());
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void cancellationAuthorization() {
        var ctx = new OrgContext("cancel");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Cancel me", null, IncidentSeverity.SEV3, null, null));

        assertThatThrownBy(() -> incidentService.updateIncident(
                        ctx.organization.getId(),
                        incident.getId(),
                        ctx.member.getId(),
                        IncidentUpdateSpec.builder().status(IncidentStatus.CANCELLED).build()))
                .isInstanceOf(ForbiddenException.class);

        Incident cancelled = incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.admin.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.CANCELLED).build());
        assertThat(cancelled.getCancelledAt()).isNotNull();
    }

    @Test
    void terminalIncidentCannotBeModified() {
        var ctx = new OrgContext("terminal");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Done", null, IncidentSeverity.SEV4, null, null));
        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.RESOLVED).build());

        assertThatThrownBy(() -> incidentService.updateIncident(
                        ctx.organization.getId(),
                        incident.getId(),
                        ctx.member.getId(),
                        IncidentUpdateSpec.builder().title("Nope").build()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void incidentNumberRollsBackWithFailedCreationTransaction() {
        var ctx = new OrgContext("rollback");
        UUID organizationId = ctx.organization.getId();

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
                    long number = incidentNumberAllocator.allocateNextIncidentNumber(organizationId);
                    assertThat(number).isEqualTo(1L);
                    Incident broken = new Incident();
                    broken.setOrganization(ctx.organization);
                    broken.setIncidentNumber(number);
                    broken.setTitle("Missing reporter");
                    broken.setSeverity(IncidentSeverity.SEV1);
                    broken.setStatus(IncidentStatus.OPEN);
                    incidentRepository.saveAndFlush(broken);
                    return null;
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        Incident recovered = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Recovered", null, IncidentSeverity.SEV2, null, null));

        assertThat(recovered.getIncidentNumber()).isEqualTo(1L);
    }

    private final class OrgContext {
        final Organization organization;
        final User owner;
        final User member;
        final User admin;
        final User viewer;

        OrgContext(String slugPrefix) {
            owner = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-owner@example.com");
            member = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-member@example.com");
            admin = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-admin@example.com");
            viewer = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-viewer@example.com");
            organization = IncidentTestFixtures.createOrganization(
                    organizationRepository, IncidentTestFixtures.uniqueSlug(slugPrefix));
            addMember(owner, OrganizationRole.OWNER);
            addMember(member, OrganizationRole.MEMBER);
            addMember(admin, OrganizationRole.ADMIN);
            addMember(viewer, OrganizationRole.VIEWER);
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
