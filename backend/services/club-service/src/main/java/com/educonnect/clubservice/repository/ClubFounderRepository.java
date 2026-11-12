package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubFounder;
import com.educonnect.clubservice.model.FounderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubFounderRepository extends JpaRepository<ClubFounder, UUID> {

    List<ClubFounder> findByRequestId(UUID requestId);

    List<ClubFounder> findByRequestIdIn(Collection<UUID> requestIds);

    Optional<ClubFounder> findByRequestIdAndStudentId(UUID requestId, UUID studentId);

    List<ClubFounder> findByStudentId(UUID studentId);

    List<ClubFounder> findByStudentIdAndStatus(UUID studentId, FounderStatus status);
}
