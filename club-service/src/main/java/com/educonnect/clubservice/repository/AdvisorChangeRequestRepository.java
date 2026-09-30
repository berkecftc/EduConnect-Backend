package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.AdvisorChangeRequest;
import com.educonnect.clubservice.model.AdvisorChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdvisorChangeRequestRepository extends JpaRepository<AdvisorChangeRequest, UUID> {

    List<AdvisorChangeRequest> findByClubIdOrderByCreatedAtDesc(UUID clubId);

    List<AdvisorChangeRequest> findByClubIdAndStatus(UUID clubId, AdvisorChangeRequestStatus status);

    List<AdvisorChangeRequest> findByProposedAdvisorIdAndStatusOrderByCreatedAtAsc(UUID proposedAdvisorId,
                                                                                  AdvisorChangeRequestStatus status);

    boolean existsByClubIdAndStatus(UUID clubId, AdvisorChangeRequestStatus status);
}
