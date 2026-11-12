package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubElectionCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubElectionCandidateRepository extends JpaRepository<ClubElectionCandidate, UUID> {

    List<ClubElectionCandidate> findByElectionIdOrderByCreatedAt(UUID electionId);

    List<ClubElectionCandidate> findByElectionIdInOrderByCreatedAt(Collection<UUID> electionIds);

    Optional<ClubElectionCandidate> findByElectionIdAndStudentId(UUID electionId, UUID studentId);
}
