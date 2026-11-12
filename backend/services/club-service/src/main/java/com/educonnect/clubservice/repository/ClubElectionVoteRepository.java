package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubElectionVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClubElectionVoteRepository extends JpaRepository<ClubElectionVote, UUID> {

    List<ClubElectionVote> findByElectionId(UUID electionId);
}
