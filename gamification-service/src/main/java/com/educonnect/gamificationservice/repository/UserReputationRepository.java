package com.educonnect.gamificationservice.repository;

import com.educonnect.gamificationservice.model.UserReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface UserReputationRepository extends JpaRepository<UserReputation, UUID> {

    @Modifying
    @Query("UPDATE UserReputation u SET u.currentStreak = 0 WHERE u.currentStreak > 0 AND u.lastLoginDate IS NOT NULL AND u.lastLoginDate < :yesterday")
    int resetInactiveStreaks(@Param("yesterday") LocalDate yesterday);
}

