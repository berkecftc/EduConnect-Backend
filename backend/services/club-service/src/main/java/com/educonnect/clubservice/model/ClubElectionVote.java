package com.educonnect.clubservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "club_election_votes")
public class ClubElectionVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    protected ClubElectionVote() {
    }

    public ClubElectionVote(UUID electionId, UUID candidateId) {
        this.electionId = electionId;
        this.candidateId = candidateId;
    }

    public UUID getCandidateId() { return candidateId; }
}
