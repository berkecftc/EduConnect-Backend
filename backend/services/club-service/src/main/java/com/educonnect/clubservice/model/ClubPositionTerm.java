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
@Table(name = "club_position_terms")
public class ClubPositionTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClubPosition position;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private PositionEndReason endReason;

    protected ClubPositionTerm() {
    }

    public ClubPositionTerm(UUID clubId, UUID studentId, ClubPosition position, Instant startedAt) {
        this.clubId = clubId;
        this.studentId = studentId;
        this.position = position;
        this.startedAt = startedAt;
    }

    public void end(Instant at, PositionEndReason reason) {
        this.endedAt = at;
        this.endReason = reason;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public UUID getStudentId() { return studentId; }
    public ClubPosition getPosition() { return position; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public PositionEndReason getEndReason() { return endReason; }
}
