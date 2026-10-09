package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.user.User;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_postmortems", schema = "app")
@Access(AccessType.FIELD)
public class IncidentPostmortem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentPostmortemStatus status;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String impact;

    @Column(name = "root_cause", nullable = false, columnDefinition = "TEXT")
    private String rootCause;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "lessons_learned", nullable = false, columnDefinition = "TEXT")
    private String lessonsLearned;

    @Column(name = "corrective_actions", nullable = false, columnDefinition = "TEXT")
    private String correctiveActions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by_id")
    private User publishedBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected IncidentPostmortem() {}

    public static IncidentPostmortem create(
            Organization organization, Incident incident, User author, String title) {
        IncidentPostmortem postmortem = new IncidentPostmortem();
        postmortem.organization = organization;
        postmortem.incident = incident;
        postmortem.author = author;
        postmortem.title = title;
        postmortem.status = IncidentPostmortemStatus.DRAFT;
        postmortem.summary = "";
        postmortem.impact = "";
        postmortem.rootCause = "";
        postmortem.resolution = "";
        postmortem.lessonsLearned = "";
        postmortem.correctiveActions = "";
        return postmortem;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void updateTitle(String newTitle) {
        this.title = newTitle;
    }

    public void updateSummary(String newSummary) {
        this.summary = newSummary;
    }

    public void updateImpact(String newImpact) {
        this.impact = newImpact;
    }

    public void updateRootCause(String newRootCause) {
        this.rootCause = newRootCause;
    }

    public void updateResolution(String newResolution) {
        this.resolution = newResolution;
    }

    public void updateLessonsLearned(String newLessonsLearned) {
        this.lessonsLearned = newLessonsLearned;
    }

    public void updateCorrectiveActions(String newCorrectiveActions) {
        this.correctiveActions = newCorrectiveActions;
    }

    public void assignStatus(IncidentPostmortemStatus newStatus) {
        this.status = newStatus;
    }

    public void assignPublicationMetadata(Instant publishedAt, User publishedBy) {
        this.publishedAt = publishedAt;
        this.publishedBy = publishedBy;
    }

    public void clearPublicationMetadata() {
        this.publishedAt = null;
        this.publishedBy = null;
    }

    public void assignArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public void clearArchivedAt() {
        this.archivedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Incident getIncident() {
        return incident;
    }

    public User getAuthor() {
        return author;
    }

    public IncidentPostmortemStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getImpact() {
        return impact;
    }

    public String getRootCause() {
        return rootCause;
    }

    public String getResolution() {
        return resolution;
    }

    public String getLessonsLearned() {
        return lessonsLearned;
    }

    public String getCorrectiveActions() {
        return correctiveActions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public User getPublishedBy() {
        return publishedBy;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    void setCreatedAtForTesting(Instant createdAt) {
        this.createdAt = createdAt;
    }

    void setPublishedAtForTesting(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
