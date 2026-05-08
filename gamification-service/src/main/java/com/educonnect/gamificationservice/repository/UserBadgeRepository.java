package com.educonnect.gamificationservice.repository;

import com.educonnect.gamificationservice.model.BadgeType;
import com.educonnect.gamificationservice.model.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    List<UserBadge> findByUserIdOrderByEarnedAtAsc(UUID userId);

    boolean existsByUserIdAndBadgeType(UUID userId, BadgeType badgeType);
}

