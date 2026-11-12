package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubElection;
import com.educonnect.clubservice.model.ElectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubElectionRepository extends JpaRepository<ClubElection, UUID> {

    boolean existsByClubIdAndStatusIn(UUID clubId, Collection<ElectionStatus> statuses);

    List<ClubElection> findByClubIdOrderByOpenedAtDesc(UUID clubId);

    Optional<ClubElection> findByRequestId(UUID requestId);

    List<ClubElection> findByRequestIdIn(Collection<UUID> requestIds);
}
