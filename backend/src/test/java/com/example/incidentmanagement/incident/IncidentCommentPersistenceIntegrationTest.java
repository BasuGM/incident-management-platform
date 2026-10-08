package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class IncidentCommentPersistenceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentCommentRepository incidentCommentRepository;

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
    void persistsCommentWithRelationshipsAndTimestamps() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "author@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("cmt"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentComment comment = IncidentTestFixtures.newComment(organization, incident, author, "Investigating.");
        IncidentComment saved = incidentCommentRepository.saveAndFlush(comment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrganization().getId()).isEqualTo(organization.getId());
        assertThat(saved.getIncident().getId()).isEqualTo(incident.getId());
        assertThat(saved.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(saved.getBody()).isEqualTo("Investigating.");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void generatesUuidPrimaryKey() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "uuid@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("uuid"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentComment saved = incidentCommentRepository.saveAndFlush(
                IncidentTestFixtures.newComment(organization, incident, author, "Hello"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void softDeleteClearsBodyAndSetsDeletedAt() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "del@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("del"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentComment comment =
                incidentCommentRepository.saveAndFlush(
                        IncidentTestFixtures.newComment(organization, incident, author, "Remove me"));
        UUID commentId = comment.getId();

        comment.markDeleted();
        incidentCommentRepository.saveAndFlush(comment);

        IncidentComment reloaded = incidentCommentRepository.findById(commentId).orElseThrow();
        assertThat(reloaded.getDeletedAt()).isNotNull();
        assertThat(reloaded.getBody()).isEmpty();
        assertThat(reloaded.isDeleted()).isTrue();
    }

    @Test
    void softDeletedCommentRemainsInIncidentScopedQuery() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "tomb@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("tomb"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentComment comment =
                incidentCommentRepository.saveAndFlush(
                        IncidentTestFixtures.newComment(organization, incident, author, "Gone"));
        comment.markDeleted();
        incidentCommentRepository.saveAndFlush(comment);

        var page = incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                organization.getId(), incident.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getDeletedAt()).isNotNull();
    }

    @Test
    void updateBodyChangesUpdatedAtButNotCreatedAt() throws InterruptedException {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "upd@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("upd"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentComment comment =
                incidentCommentRepository.saveAndFlush(
                        IncidentTestFixtures.newComment(organization, incident, author, "Original"));
        Instant createdAt = comment.getCreatedAt();
        Instant updatedAtBefore = comment.getUpdatedAt();

        Thread.sleep(5);
        comment.updateBody("Revised");
        IncidentComment saved = incidentCommentRepository.saveAndFlush(comment);

        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(updatedAtBefore);
        assertThat(saved.getBody()).isEqualTo("Revised");
    }

    @Test
    void organizationAndIncidentScopingIsolatesTenants() {
        User authorA = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "a@example.com");
        User authorB = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "b@example.com");
        Organization orgA =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("org-a"));
        Organization orgB =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("org-b"));
        Incident incidentA = persistIncident(orgA, authorA, 1L);
        Incident incidentB = persistIncident(orgB, authorB, 1L);

        incidentCommentRepository.saveAndFlush(
                IncidentTestFixtures.newComment(orgA, incidentA, authorA, "Comment A"));
        incidentCommentRepository.saveAndFlush(
                IncidentTestFixtures.newComment(orgB, incidentB, authorB, "Comment B"));

        var pageA = incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                orgA.getId(), incidentA.getId(), PageRequest.of(0, 10));
        var pageB = incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                orgB.getId(), incidentB.getId(), PageRequest.of(0, 10));
        var crossPage = incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                orgA.getId(), incidentB.getId(), PageRequest.of(0, 10));

        assertThat(pageA.getTotalElements()).isEqualTo(1);
        assertThat(pageA.getContent().getFirst().getBody()).isEqualTo("Comment A");
        assertThat(pageB.getTotalElements()).isEqualTo(1);
        assertThat(pageB.getContent().getFirst().getBody()).isEqualTo("Comment B");
        assertThat(crossPage.getTotalElements()).isZero();
    }

    @Test
    void returnsCommentsInChronologicalOrder() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "ord@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("ord"));
        Incident incident = persistIncident(organization, author, 1L);

        Instant t1 = Instant.parse("2026-02-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-02-01T10:00:01Z");
        Instant t3 = Instant.parse("2026-02-01T10:00:02Z");

        IncidentComment first = IncidentTestFixtures.newComment(organization, incident, author, "First");
        first.setCreatedAtForTesting(t1);
        first = incidentCommentRepository.saveAndFlush(first);

        IncidentComment second = IncidentTestFixtures.newComment(organization, incident, author, "Second");
        second.setCreatedAtForTesting(t2);
        second = incidentCommentRepository.saveAndFlush(second);

        IncidentComment third = IncidentTestFixtures.newComment(organization, incident, author, "Third");
        third.setCreatedAtForTesting(t3);
        third = incidentCommentRepository.saveAndFlush(third);

        List<IncidentComment> ordered = incidentCommentRepository
                .findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                        organization.getId(), incident.getId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(ordered).extracting(IncidentComment::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void supportsPaginationWithoutDuplicatesOrGaps() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "page@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("page"));
        Incident incident = persistIncident(organization, author, 1L);

        for (int i = 0; i < 5; i++) {
            incidentCommentRepository.saveAndFlush(
                    IncidentTestFixtures.newComment(organization, incident, author, "Message " + i));
        }

        var page0 = incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                organization.getId(), incident.getId(), PageRequest.of(0, 2));
        var page1 = incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                organization.getId(), incident.getId(), PageRequest.of(1, 2));

        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);

        Set<UUID> ids = new HashSet<>();
        page0.getContent().forEach(c -> ids.add(c.getId()));
        page1.getContent().forEach(c -> ids.add(c.getId()));
        assertThat(ids).hasSize(4);

        List<IncidentComment> combined = new ArrayList<>();
        combined.addAll(page0.getContent());
        combined.addAll(page1.getContent());
        combined.sort(Comparator.comparing(IncidentComment::getCreatedAt).thenComparing(IncidentComment::getId));
        assertThat(combined).isSortedAccordingTo(Comparator.comparing(IncidentComment::getCreatedAt)
                .thenComparing(IncidentComment::getId));
    }

    @Test
    void preservesMultilineBody() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "lines@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("lines"));
        Incident incident = persistIncident(organization, author, 1L);

        String body = "Investigating the API.\n\nDatabase metrics look normal.";
        IncidentComment saved = incidentCommentRepository.saveAndFlush(
                IncidentTestFixtures.newComment(organization, incident, author, body));

        assertThat(saved.getBody()).isEqualTo(body);
    }

    @Test
    void newCommentsHaveNullDeletedAt() {
        User author = IncidentTestFixtures.createUser(userRepository, passwordEncoder, "new@example.com");
        Organization organization =
                IncidentTestFixtures.createOrganization(organizationRepository, IncidentTestFixtures.uniqueSlug("new"));
        Incident incident = persistIncident(organization, author, 1L);

        IncidentComment saved = incidentCommentRepository.saveAndFlush(
                IncidentTestFixtures.newComment(organization, incident, author, "Active"));

        assertThat(saved.getDeletedAt()).isNull();
        assertThat(incidentCommentRepository
                        .findByIdAndOrganization_IdAndIncident_Id(saved.getId(), organization.getId(), incident.getId())
                        .orElseThrow()
                        .getDeletedAt())
                .isNull();
    }

    private Incident persistIncident(Organization organization, User reporter, long number) {
        return incidentRepository.saveAndFlush(IncidentTestFixtures.newIncident(
                organization, number, reporter, "Incident", IncidentSeverity.SEV2));
    }
}
