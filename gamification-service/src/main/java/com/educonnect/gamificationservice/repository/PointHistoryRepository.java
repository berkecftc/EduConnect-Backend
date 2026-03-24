package com.educonnect.gamificationservice.repository;

import com.educonnect.gamificationservice.model.ActionType;
import com.educonnect.gamificationservice.model.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PointHistoryRepository extends JpaRepository<PointHistory, UUID> {

    boolean existsByUserIdAndActionTypeAndReferenceId(UUID userId, ActionType actionType, String referenceId);
}

