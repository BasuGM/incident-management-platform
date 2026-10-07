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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class IncidentServiceEventIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentEventRepository incidentEventRepository;

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

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanAll();
    }

    @Test
    void createProducesSingleIncidentCreatedEvent() {
        var ctx = new OrgContext("created");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Outage", "details", IncidentSeverity.SEV2, null, null));

        List<IncidentEvent> events = eventsFor(incident);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getEventType()).isEqualTo(IncidentEventType.INCIDENT_CREATED);
        assertThat(events.getFirst().getActor().getId()).isEqualTo(ctx.member.getId());
        assertThat(events.getFirst().getOrganization().getId()).isEqualTo(ctx.organization.getId());
        Map<String, Object> payload = events.getFirst().getPayload();
        assertThat(payload.get("title")).isEqualTo("Outage");
        assertThat(payload.get("severity")).isEqualTo("SEV2");
        assertThat(payload.get("status")).isEqualTo("OPEN");
        assertThat(payload.get("description")).isEqualTo("details");
        assertThat(payload.get("serviceId")).isNull();
        assertThat(payload.get("commanderId")).isNull();
    }

    @Test
    void failedCreateProducesNoEvents() {
        var ctxA = new OrgContext("fail-a");
        var ctxB = new OrgContext("fail-b");
        Service serviceB = IncidentTestFixtures.createService(serviceRepository, ctxB.organization, "other");

        assertThatThrownBy(() -> incidentService.createIncident(
                        ctxA.organization.getId(),
                        ctxA.member.getId(),
                        new CreateIncidentParams("Bad", null, IncidentSeverity.SEV2, serviceB.getId(), null)))
                .isInstanceOf(ForbiddenException.class);

        assertThat(incidentEventRepository.count()).isZero();
        assertThat(incidentRepository.count()).isZero();
    }

    @Test
    void statusTransitionsRecordStatusChanged() {
        var ctx = new OrgContext("status");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Status", null, IncidentSeverity.SEV2, null, null));

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.ACKNOWLEDGED).build());

        assertThat(countEvents(incident, IncidentEventType.STATUS_CHANGED)).isEqualTo(1);
        assertOldNew(eventsFor(incident).stream()
                .filter(e -> e.getEventType() == IncidentEventType.STATUS_CHANGED)
                .findFirst()
                .orElseThrow()
                .getPayload(), "OPEN", "ACKNOWLEDGED");

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.RESOLVED).build());

        assertThat(countEvents(incident, IncidentEventType.STATUS_CHANGED)).isEqualTo(2);
    }

    @Test
    void openToResolvedRecordsSingleStatusChanged() {
        var ctx = new OrgContext("open-resolved");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Fast", null, IncidentSeverity.SEV1, null, null));

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.RESOLVED).build());

        assertThat(countEvents(incident, IncidentEventType.STATUS_CHANGED)).isEqualTo(1);
        assertOldNew(latestEvent(incident, IncidentEventType.STATUS_CHANGED).getPayload(), "OPEN", "RESOLVED");
    }

    @Test
    void invalidStatusTransitionProducesNoStatusEvent() {
        var ctx = new OrgContext("invalid-status");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Stuck", null, IncidentSeverity.SEV2, null, null));
        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.ACKNOWLEDGED).build());
        long statusEventsBefore = countEvents(incident, IncidentEventType.STATUS_CHANGED);

        assertThatThrownBy(() -> incidentService.updateIncident(
                        ctx.organization.getId(),
                        incident.getId(),
                        ctx.member.getId(),
                        IncidentUpdateSpec.builder().status(IncidentStatus.OPEN).build()))
                .isInstanceOf(ConflictException.class);

        assertThat(countEvents(incident, IncidentEventType.STATUS_CHANGED)).isEqualTo(statusEventsBefore);
    }

    @Test
    void severityChangeAndNoOp() {
        var ctx = new OrgContext("severity");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Sev", null, IncidentSeverity.SEV2, null, null));

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.admin.getId(),
                IncidentUpdateSpec.builder().severity(IncidentSeverity.SEV1).build());

        assertThat(countEvents(incident, IncidentEventType.SEVERITY_CHANGED)).isEqualTo(1);
        assertOldNew(latestEvent(incident, IncidentEventType.SEVERITY_CHANGED).getPayload(), "SEV2", "SEV1");

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.admin.getId(),
                IncidentUpdateSpec.builder().severity(IncidentSeverity.SEV1).build());

        assertThat(countEvents(incident, IncidentEventType.SEVERITY_CHANGED)).isEqualTo(1);
    }

    @Test
    void serviceChanges() {
        var ctx = new OrgContext("service");
        Service serviceA = IncidentTestFixtures.createService(serviceRepository, ctx.organization, "a");
        Service serviceB = IncidentTestFixtures.createService(serviceRepository, ctx.organization, "b");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Svc", null, IncidentSeverity.SEV3, null, null));

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().serviceId(serviceA.getId()).build());
        assertThat(countEvents(incident, IncidentEventType.SERVICE_CHANGED)).isEqualTo(1);
        assertOldNewUuid(latestEvent(incident, IncidentEventType.SERVICE_CHANGED).getPayload(), null, serviceA.getId());

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().serviceId(serviceB.getId()).build());
        assertThat(countEvents(incident, IncidentEventType.SERVICE_CHANGED)).isEqualTo(2);
        assertOldNewUuid(latestEvent(incident, IncidentEventType.SERVICE_CHANGED).getPayload(), serviceA.getId(), serviceB.getId());

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().clearService().build());
        assertThat(countEvents(incident, IncidentEventType.SERVICE_CHANGED)).isEqualTo(3);
        assertOldNewUuid(latestEvent(incident, IncidentEventType.SERVICE_CHANGED).getPayload(), serviceB.getId(), null);

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().serviceId(serviceB.getId()).build());
        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().serviceId(serviceB.getId()).build());
        assertThat(countEvents(incident, IncidentEventType.SERVICE_CHANGED)).isEqualTo(4);
    }

    @Test
    void commanderChanges() {
        var ctx = new OrgContext("commander");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Cmd", null, IncidentSeverity.SEV3, null, null));

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().commanderId(ctx.admin.getId()).build());
        assertThat(countEvents(incident, IncidentEventType.COMMANDER_CHANGED)).isEqualTo(1);
        assertOldNewUuid(
                latestEvent(incident, IncidentEventType.COMMANDER_CHANGED).getPayload(), null, ctx.admin.getId());

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().commanderId(ctx.owner.getId()).build());
        assertOldNewUuid(
                latestEvent(incident, IncidentEventType.COMMANDER_CHANGED).getPayload(),
                ctx.admin.getId(),
                ctx.owner.getId());

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().clearCommander().build());
        assertOldNewUuid(
                latestEvent(incident, IncidentEventType.COMMANDER_CHANGED).getPayload(), ctx.owner.getId(), null);

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().commanderId(ctx.admin.getId()).build());
        assertThat(countEvents(incident, IncidentEventType.COMMANDER_CHANGED)).isEqualTo(4);

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().commanderId(ctx.admin.getId()).build());
        assertThat(countEvents(incident, IncidentEventType.COMMANDER_CHANGED)).isEqualTo(4);
    }

    @Test
    void titleAndDescriptionChanges() {
        var ctx = new OrgContext("text");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Original", "alpha", IncidentSeverity.SEV4, null, null));

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().title("Original").build());
        assertThat(countEvents(incident, IncidentEventType.TITLE_CHANGED)).isZero();

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().title("Renamed").build());
        assertThat(countEvents(incident, IncidentEventType.TITLE_CHANGED)).isEqualTo(1);
        assertOldNew(latestEvent(incident, IncidentEventType.TITLE_CHANGED).getPayload(), "Original", "Renamed");

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().description("beta").build());
        assertOldNew(latestEvent(incident, IncidentEventType.DESCRIPTION_CHANGED).getPayload(), "alpha", "beta");

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().description("alpha").build());
        assertThat(countEvents(incident, IncidentEventType.DESCRIPTION_CHANGED)).isEqualTo(2);

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().description("").build());
        assertThat(countEvents(incident, IncidentEventType.DESCRIPTION_CHANGED)).isEqualTo(3);
        assertOldNew(latestEvent(incident, IncidentEventType.DESCRIPTION_CHANGED).getPayload(), "alpha", null);
    }

    @Test
    void multiFieldPatchCreatesOneEventPerChangedField() {
        var ctx = new OrgContext("multi");
        Service service = IncidentTestFixtures.createService(serviceRepository, ctx.organization, "multi");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Before", "desc", IncidentSeverity.SEV3, null, null));

        Incident updated = incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.admin.getId(),
                IncidentUpdateSpec.builder()
                        .title("After")
                        .severity(IncidentSeverity.SEV1)
                        .serviceId(service.getId())
                        .commanderId(ctx.member.getId())
                        .status(IncidentStatus.ACKNOWLEDGED)
                        .build());

        assertThat(updated.getTitle()).isEqualTo("After");
        assertThat(updated.getSeverity()).isEqualTo(IncidentSeverity.SEV1);
        assertThat(updated.getService().getId()).isEqualTo(service.getId());
        assertThat(updated.getCommander().getId()).isEqualTo(ctx.member.getId());
        assertThat(updated.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);

        Map<IncidentEventType, Long> counts = eventsFor(incident).stream()
                .filter(e -> e.getEventType() != IncidentEventType.INCIDENT_CREATED)
                .collect(Collectors.groupingBy(IncidentEvent::getEventType, Collectors.counting()));

        assertThat(counts.get(IncidentEventType.TITLE_CHANGED)).isEqualTo(1L);
        assertThat(counts.get(IncidentEventType.SEVERITY_CHANGED)).isEqualTo(1L);
        assertThat(counts.get(IncidentEventType.SERVICE_CHANGED)).isEqualTo(1L);
        assertThat(counts.get(IncidentEventType.COMMANDER_CHANGED)).isEqualTo(1L);
        assertThat(counts.get(IncidentEventType.STATUS_CHANGED)).isEqualTo(1L);

        List<IncidentEvent> mutationEvents = eventsFor(incident).stream()
                .filter(e -> e.getEventType() != IncidentEventType.INCIDENT_CREATED)
                .toList();
        assertThat(mutationEvents).allMatch(e -> e.getActor().getId().equals(ctx.admin.getId()));
        assertThat(mutationEvents).allMatch(e -> e.getIncident().getId().equals(incident.getId()));
        assertThat(mutationEvents).allMatch(e -> e.getOrganization().getId().equals(ctx.organization.getId()));

        List<IncidentEventType> recordedOrder = mutationEvents.stream()
                .sorted(Comparator.comparing(IncidentEvent::getCreatedAt).thenComparing(IncidentEvent::getId))
                .map(IncidentEvent::getEventType)
                .toList();
        assertThat(recordedOrder).containsExactly(
                IncidentEventType.TITLE_CHANGED,
                IncidentEventType.SEVERITY_CHANGED,
                IncidentEventType.SERVICE_CHANGED,
                IncidentEventType.COMMANDER_CHANGED,
                IncidentEventType.STATUS_CHANGED);
    }

    @Test
    void severityChangeActorIsUpdaterNotReporter() {
        var ctx = new OrgContext("actor");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Actor", null, IncidentSeverity.SEV2, null, null));

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.admin.getId(),
                IncidentUpdateSpec.builder().severity(IncidentSeverity.SEV1).build());

        IncidentEvent severityEvent = latestEvent(incident, IncidentEventType.SEVERITY_CHANGED);
        assertThat(severityEvent.getActor().getId()).isEqualTo(ctx.admin.getId());
        assertThat(severityEvent.getActor().getId()).isNotEqualTo(ctx.member.getId());
    }

    @Test
    void statusChangeActorIsResolver() {
        var ctx = new OrgContext("resolver");
        Incident incident = incidentService.createIncident(
                ctx.organization.getId(),
                ctx.member.getId(),
                new CreateIncidentParams("Resolve", null, IncidentSeverity.SEV2, null, null));

        incidentService.updateIncident(
                ctx.organization.getId(),
                incident.getId(),
                ctx.member.getId(),
                IncidentUpdateSpec.builder().status(IncidentStatus.RESOLVED).build());

        IncidentEvent statusEvent = latestEvent(incident, IncidentEventType.STATUS_CHANGED);
        assertThat(statusEvent.getActor().getId()).isEqualTo(ctx.member.getId());
    }

    private IncidentEvent latestEvent(Incident incident, IncidentEventType type) {
        return eventsFor(incident).stream()
                .filter(e -> e.getEventType() == type)
                .findFirst()
                .orElseThrow();
    }

    private List<IncidentEvent> eventsFor(Incident incident) {
        return incidentEventRepository
                .findByOrganization_IdAndIncident_IdOrderByCreatedAtDescIdDesc(
                        incident.getOrganization().getId(), incident.getId(), Pageable.unpaged())
                .getContent();
    }

    private long countEvents(Incident incident, IncidentEventType type) {
        return eventsFor(incident).stream().filter(e -> e.getEventType() == type).count();
    }

    private static void assertOldNew(Map<String, Object> payload, Object oldValue, Object newValue) {
        assertThat(payload.get("old")).isEqualTo(oldValue);
        assertThat(payload.get("new")).isEqualTo(newValue);
    }

    private static void assertOldNewUuid(Map<String, Object> payload, UUID oldValue, UUID newValue) {
        assertThat(payload.get("old")).isEqualTo(oldValue == null ? null : oldValue.toString());
        assertThat(payload.get("new")).isEqualTo(newValue == null ? null : newValue.toString());
    }

    private final class OrgContext {
        final Organization organization;
        final User owner;
        final User member;
        final User admin;

        OrgContext(String slugPrefix) {
            owner = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-owner@example.com");
            member = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-member@example.com");
            admin = IncidentTestFixtures.createUser(userRepository, passwordEncoder, slugPrefix + "-admin@example.com");
            organization = IncidentTestFixtures.createOrganization(
                    organizationRepository, IncidentTestFixtures.uniqueSlug(slugPrefix));
            addMember(owner, OrganizationRole.OWNER);
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
