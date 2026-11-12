package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "club_elections")
public class ClubElection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ElectionStatus status = ElectionStatus.CANDIDACY;

    @Column(name = "board_seats", nullable = false)
    private int boardSeats;

    @Column(name = "audit_seats", nullable = false)
    private int auditSeats;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "opened_by")
    private UUID openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "voting_started_at")
    private Instant votingStartedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "eligible_voters")
    private Integer eligibleVoters;

    @Column(name = "voters")
    private Integer voters;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ClubElection() {
    }

    public ClubElection(UUID clubId, int boardSeats, int auditSeats, String note, UUID openedBy, Instant openedAt) {
        this.clubId = clubId;
        this.boardSeats = boardSeats;
        this.auditSeats = auditSeats;
        this.note = note;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
    }

    public int seatsOf(ElectionBallot ballot) {
        return switch (ballot) {
            case PRESIDENT -> 1;
            case BOARD -> boardSeats;
            case AUDIT -> auditSeats;
        };
    }

    public void startVoting(Instant at) {
        this.status = ElectionStatus.VOTING;
        this.votingStartedAt = at;
    }

    public void close(int eligibleVoters, int voters, UUID requestId, Instant at) {
        this.status = ElectionStatus.AWAITING_APPROVAL;
        this.eligibleVoters = eligibleVoters;
        this.voters = voters;
        this.requestId = requestId;
        this.closedAt = at;
    }

    public void complete(Instant at) {
        this.status = ElectionStatus.COMPLETED;
        this.completedAt = at;
    }

    public void cancel(Instant at) {
        this.status = ElectionStatus.CANCELLED;
        this.completedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getClubId() { return clubId; }
    public ElectionStatus getStatus() { return status; }
    public int getBoardSeats() { return boardSeats; }
    public int getAuditSeats() { return auditSeats; }
    public String getNote() { return note; }
    public UUID getOpenedBy() { return openedBy; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getVotingStartedAt() { return votingStartedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Integer getEligibleVoters() { return eligibleVoters; }
    public Integer getVoters() { return voters; }
    public UUID getRequestId() { return requestId; }
    public Instant getCompletedAt() { return completedAt; }
}
