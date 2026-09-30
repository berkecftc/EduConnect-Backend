package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubMeeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubMeetingRepository extends JpaRepository<ClubMeeting, UUID> {

    Optional<ClubMeeting> findByRequestId(UUID requestId);

    List<ClubMeeting> findByRequestIdIn(Collection<UUID> requestIds);

    List<ClubMeeting> findByClubIdAndAcademicYearOrderByMeetingAtDesc(UUID clubId, int academicYear);
}
