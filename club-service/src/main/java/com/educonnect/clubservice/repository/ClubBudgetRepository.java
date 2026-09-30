package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubBudgetRepository extends JpaRepository<ClubBudget, UUID> {

    Optional<ClubBudget> findByRequestId(UUID requestId);

    List<ClubBudget> findByRequestIdIn(Collection<UUID> requestIds);

    List<ClubBudget> findByClubIdOrderByCreatedAtDesc(UUID clubId);

    Optional<ClubBudget> findFirstByClubIdAndAcademicYearAndApprovedAtIsNotNullOrderByApprovedAtDesc(UUID clubId, int academicYear);
}
