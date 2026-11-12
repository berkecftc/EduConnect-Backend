package com.educonnect.eventservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_staff")
public class EventStaff {

    public enum Status {
        PENDING,
        APPROVED
    }

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "proposed_by", nullable = false)
    private UUID proposedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected EventStaff() {
    }

    public EventStaff(UUID eventId, UUID userId, UUID proposedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.userId = userId;
        this.proposedBy = proposedBy;
        this.status = Status.PENDING;
        this.createdAt = now;
    }

    public void approve(UUID approverId, Instant now) {
        this.status = Status.APPROVED;
        this.approvedBy = approverId;
        this.decidedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public Status getStatus() { return status; }
    public UUID getProposedBy() { return proposedBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
