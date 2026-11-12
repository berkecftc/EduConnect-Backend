package com.educonnect.gamificationservice.repository;

import com.educonnect.gamificationservice.model.LeaderboardPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeaderboardPreferenceRepository extends JpaRepository<LeaderboardPreference, UUID> {
}
