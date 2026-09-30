package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "club_announcements")
public class ClubAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    protected ClubAnnouncement() {
    }

    public ClubAnnouncement(UUID clubId, UUID requestId, String title, String body, UUID preparedBy, Instant createdAt) {
        this.clubId = clubId;
        this.requestId = requestId;
        this.title = title;
        this.body = body;
        this.preparedBy = preparedBy;
        this.createdAt = createdAt;
    }

    public void publish(Instant at) {
        this.publishedAt = at;
    }

    public void remove(UUID by, Instant at) {
        this.removedBy = by;
        this.removedAt = at;
    }

    public boolean isVisible() {
        return publishedAt != null && removedAt == null;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public UUID getRequestId() { return requestId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public UUID getPreparedBy() { return preparedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getRemovedAt() { return removedAt; }
    public UUID getRemovedBy() { return removedBy; }
}
