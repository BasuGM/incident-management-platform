package com.example.incidentmanagement.oncall;

import com.example.incidentmanagement.user.User;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "on_call_schedule_participants", schema = "app")
@Access(AccessType.FIELD)
public class OnCallScheduleParticipant {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
    private OnCallSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OnCallScheduleParticipant() {}

    public static OnCallScheduleParticipant create(OnCallSchedule schedule, User user, int position) {
        OnCallScheduleParticipant participant = new OnCallScheduleParticipant();
        participant.schedule = schedule;
        participant.user = user;
        participant.position = position;
        return participant;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public OnCallSchedule getSchedule() {
        return schedule;
    }

    public User getUser() {
        return user;
    }

    public int getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
