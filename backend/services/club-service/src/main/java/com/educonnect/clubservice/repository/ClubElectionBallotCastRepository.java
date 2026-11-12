package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubElectionBallotCast;
import com.educonnect.clubservice.model.ElectionBallot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClubElectionBallotCastRepository extends JpaRepository<ClubElectionBallotCast, UUID> {

    boolean existsByElectionIdAndBallotAndVoterId(UUID electionId, ElectionBallot ballot, UUID voterId);

    List<ClubElectionBallotCast> findByElectionIdAndVoterId(UUID electionId, UUID voterId);

    @Query("select count(distinct c.voterId) from ClubElectionBallotCast c where c.electionId = :electionId")
    long countVoters(@Param("electionId") UUID electionId);
}
