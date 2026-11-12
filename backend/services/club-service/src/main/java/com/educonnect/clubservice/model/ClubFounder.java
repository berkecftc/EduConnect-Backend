package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "club_founders")
public class ClubFounder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FounderStatus status;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected ClubFounder() {
    }

    private ClubFounder(UUID requestId, UUID studentId, FounderStatus status, Instant respondedAt) {
        this.requestId = requestId;
        this.studentId = studentId;
        this.status = status;
        this.respondedAt = respondedAt;
    }

    public static ClubFounder requester(UUID requestId, UUID studentId, Instant at) {
        return new ClubFounder(requestId, studentId, FounderStatus.CONFIRMED, at);
    }

    public static ClubFounder invited(UUID requestId, UUID studentId) {
        return new ClubFounder(requestId, studentId, FounderStatus.INVITED, null);
    }

    public void respond(boolean confirmed, Instant at) {
        this.status = confirmed ? FounderStatus.CONFIRMED : FounderStatus.DECLINED;
        this.respondedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getStudentId() { return studentId; }
    public FounderStatus getStatus() { return status; }
    public Instant getRespondedAt() { return respondedAt; }
}
