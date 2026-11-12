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
@Table(name = "club_election_candidates")
public class ClubElectionCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ElectionBallot ballot;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "votes")
    private Integer votes;

    @Column(nullable = false)
    private boolean elected;

    protected ClubElectionCandidate() {
    }

    public ClubElectionCandidate(UUID electionId, ElectionBallot ballot, UUID studentId, Instant createdAt) {
        this.electionId = electionId;
        this.ballot = ballot;
        this.studentId = studentId;
        this.createdAt = createdAt;
    }

    public void count(int votes, boolean elected) {
        this.votes = votes;
        this.elected = elected;
    }

    public UUID getId() { return id; }
    public UUID getElectionId() { return electionId; }
    public ElectionBallot getBallot() { return ballot; }
    public UUID getStudentId() { return studentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getVotes() { return votes; }
    public boolean isElected() { return elected; }
}
