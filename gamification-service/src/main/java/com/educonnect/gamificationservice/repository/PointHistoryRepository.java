package com.educonnect.gamificationservice.repository;

import com.educonnect.gamificationservice.model.ActionType;
import com.educonnect.gamificationservice.model.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Repository
public interface PointHistoryRepository extends JpaRepository<PointHistory, UUID> {

    boolean existsByUserIdAndActionTypeAndReferenceId(UUID userId, ActionType actionType, String referenceId);

    long countByUserIdAndActionTypeAndCreatedAtBetweenAndPointsEarnedGreaterThan(
            UUID userId,
            ActionType actionType,
            Instant start,
            Instant end,
            Integer minPoints
    );

    List<PointHistory> findByUserIdAndContentId(UUID userId, UUID contentId);

    @Query("select h.userId as userId, sum(h.pointsEarned) as points from PointHistory h "
            + "where h.createdAt >= :from and h.createdAt < :to group by h.userId "
            + "having sum(h.pointsEarned) > 0 order by sum(h.pointsEarned) desc, h.userId asc")
    List<UserPoints> totalsBetween(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
