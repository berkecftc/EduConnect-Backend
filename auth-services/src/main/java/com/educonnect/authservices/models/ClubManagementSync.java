package com.educonnect.authservices.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "club_management_sync", schema = "auth_db")
public class ClubManagementSync {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Column(name = "last_event_id", nullable = false)
    private UUID lastEventId;

    protected ClubManagementSync() {
    }

    public ClubManagementSync(UUID userId, Instant lastEventAt, UUID lastEventId) {
        this.userId = userId;
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    public UUID getUserId() { return userId; }
    public Instant getLastEventAt() { return lastEventAt; }
    public UUID getLastEventId() { return lastEventId; }

    public void record(Instant eventAt, UUID eventId) {
        this.lastEventAt = eventAt;
        this.lastEventId = eventId;
    }
}
