package com.educonnect.eventservice.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_changes")
public class EventChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventChangeKind kind;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "actor_id")
    private UUID actorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EventChange() {
    }

    public EventChange(UUID eventId, EventChangeKind kind, String details, String reason, UUID actorId) {
        this.eventId = eventId;
        this.kind = kind;
        this.details = details;
        this.reason = reason;
        this.actorId = actorId;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public EventChangeKind getKind() { return kind; }
    public String getDetails() { return details; }
    public String getReason() { return reason; }
    public UUID getActorId() { return actorId; }
    public Instant getCreatedAt() { return createdAt; }
}
