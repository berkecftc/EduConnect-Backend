package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubDecisionLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClubDecisionLogRepository extends JpaRepository<ClubDecisionLogEntry, UUID> {

    List<ClubDecisionLogEntry> findByClubIdOrderByCreatedAtDesc(UUID clubId);
}
