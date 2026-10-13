package com.example.incidentmanagement.oncall;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.service.Service;
import com.example.incidentmanagement.team.Team;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "on_call_schedules", schema = "app")
@Access(AccessType.FIELD)
public class OnCallSchedule {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnCallScheduleStatus status;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "rotation_length_days", nullable = false)
    private short rotationLengthDays;

    @Column(name = "handoff_local_time", nullable = false)
    private LocalTime handoffLocalTime;

    @Column(name = "handoff_day_of_week")
    private Short handoffDayOfWeek;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by_id", nullable = false)
    private User updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OnCallSchedule() {}

    public static OnCallSchedule createWeekly(
            Organization organization,
            String name,
            String timezone,
            LocalTime handoffLocalTime,
            short handoffDayOfWeek,
            LocalDate effectiveFrom,
            User actor) {
        OnCallSchedule schedule = new OnCallSchedule();
        schedule.organization = organization;
        schedule.name = name;
        schedule.status = OnCallScheduleStatus.ACTIVE;
        schedule.timezone = timezone;
        schedule.rotationLengthDays = 7;
        schedule.handoffLocalTime = handoffLocalTime;
        schedule.handoffDayOfWeek = handoffDayOfWeek;
        schedule.effectiveFrom = effectiveFrom;
        schedule.createdBy = actor;
        schedule.updatedBy = actor;
        return schedule;
    }

    public static OnCallSchedule createDaily(
            Organization organization,
            String name,
            String timezone,
            LocalTime handoffLocalTime,
            LocalDate effectiveFrom,
            User actor) {
        OnCallSchedule schedule = new OnCallSchedule();
        schedule.organization = organization;
        schedule.name = name;
        schedule.status = OnCallScheduleStatus.ACTIVE;
        schedule.timezone = timezone;
        schedule.rotationLengthDays = 1;
        schedule.handoffLocalTime = handoffLocalTime;
        schedule.handoffDayOfWeek = null;
        schedule.effectiveFrom = effectiveFrom;
        schedule.createdBy = actor;
        schedule.updatedBy = actor;
        return schedule;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OnCallScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(OnCallScheduleStatus status) {
        this.status = status;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public short getRotationLengthDays() {
        return rotationLengthDays;
    }

    public void setRotationLengthDays(short rotationLengthDays) {
        this.rotationLengthDays = rotationLengthDays;
    }

    public LocalTime getHandoffLocalTime() {
        return handoffLocalTime;
    }

    public void setHandoffLocalTime(LocalTime handoffLocalTime) {
        this.handoffLocalTime = handoffLocalTime;
    }

    public Short getHandoffDayOfWeek() {
        return handoffDayOfWeek;
    }

    public void setHandoffDayOfWeek(Short handoffDayOfWeek) {
        this.handoffDayOfWeek = handoffDayOfWeek;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
