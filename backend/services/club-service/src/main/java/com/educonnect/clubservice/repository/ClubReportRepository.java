package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubReport;
import com.educonnect.clubservice.model.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubReportRepository extends JpaRepository<ClubReport, UUID> {

    Optional<ClubReport> findByClubIdAndTypeAndAcademicYear(UUID clubId, ReportType type, int academicYear);

    Optional<ClubReport> findByRequestId(UUID requestId);

    List<ClubReport> findByRequestIdIn(Collection<UUID> requestIds);

    List<ClubReport> findByClubIdAndAcademicYearOrderByType(UUID clubId, int academicYear);
}
