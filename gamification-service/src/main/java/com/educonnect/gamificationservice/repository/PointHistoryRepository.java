package com.educonnect.gamificationservice.repository;

import com.educonnect.gamificationservice.model.ActionType;
import com.educonnect.gamificationservice.model.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PointHistoryRepository extends JpaRepository<PointHistory, UUID> {

    boolean existsByUserIdAndActionTypeAndReferenceId(UUID userId, ActionType actionType, String referenceId);

    long countByUserIdAndActionTypeAndCreatedAtBetweenAndPointsEarnedGreaterThan(
            UUID userId,
            ActionType actionType,
            LocalDateTime start,
            LocalDateTime end,
            Integer minPoints
    );

    List<PointHistory> findByUserIdAndContentId(UUID userId, UUID contentId);
}
