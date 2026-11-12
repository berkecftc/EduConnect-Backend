package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubPositionTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClubPositionTermRepository extends JpaRepository<ClubPositionTerm, UUID> {

    List<ClubPositionTerm> findByClubIdAndStudentIdAndEndedAtIsNull(UUID clubId, UUID studentId);

    List<ClubPositionTerm> findByClubIdAndEndedAtIsNull(UUID clubId);

    List<ClubPositionTerm> findByClubIdOrderByStartedAtDesc(UUID clubId);

    List<ClubPositionTerm> findByStudentIdOrderByStartedAtDesc(UUID studentId);

    List<ClubPositionTerm> findByClubIdAndPositionAndEndedAtIsNotNullOrderByEndedAtDesc(UUID clubId, ClubPosition position);
}
