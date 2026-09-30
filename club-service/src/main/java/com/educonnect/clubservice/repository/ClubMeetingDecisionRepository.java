package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubMeetingDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ClubMeetingDecisionRepository extends JpaRepository<ClubMeetingDecision, UUID> {

    List<ClubMeetingDecision> findByMeetingIdOrderByItemOrder(UUID meetingId);

    List<ClubMeetingDecision> findByMeetingIdInOrderByItemOrder(Collection<UUID> meetingIds);

    List<ClubMeetingDecision> findByClubIdAndAcademicYearAndDecisionNumberIsNotNullOrderByDecisionNumber(UUID clubId, int academicYear);

    long countByClubIdAndAcademicYearAndDecisionNumberIsNotNull(UUID clubId, int academicYear);

    @Query("select coalesce(max(d.decisionNumber), 0) from ClubMeetingDecision d "
            + "where d.clubId = :clubId and d.academicYear = :academicYear")
    int lastNumber(@Param("clubId") UUID clubId, @Param("academicYear") int academicYear);
}
