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
@Table(name = "club_election_ballots_cast")
public class ClubElectionBallotCast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ElectionBallot ballot;

    @Column(name = "voter_id", nullable = false)
    private UUID voterId;

    @Column(name = "cast_at", nullable = false)
    private Instant castAt;

    protected ClubElectionBallotCast() {
    }

    public ClubElectionBallotCast(UUID electionId, ElectionBallot ballot, UUID voterId, Instant castAt) {
        this.electionId = electionId;
        this.ballot = ballot;
        this.voterId = voterId;
        this.castAt = castAt;
    }

    public UUID getElectionId() { return electionId; }
    public ElectionBallot getBallot() { return ballot; }
    public UUID getVoterId() { return voterId; }
}
