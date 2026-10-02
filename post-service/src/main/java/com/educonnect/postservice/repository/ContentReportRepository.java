package com.educonnect.postservice.repository;

import com.educonnect.postservice.model.ContentReport;
import com.educonnect.postservice.model.ModerationTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ContentReportRepository extends JpaRepository<ContentReport, UUID> {

    boolean existsByTargetTypeAndTargetIdAndReporterIdAndStatus(ModerationTarget targetType, UUID targetId, UUID reporterId,
                                                                ContentReport.Status status);

    long countByTargetTypeAndTargetIdAndStatus(ModerationTarget targetType, UUID targetId, ContentReport.Status status);

    List<ContentReport> findByTargetTypeAndTargetIdAndStatus(ModerationTarget targetType, UUID targetId,
                                                             ContentReport.Status status);

    @Query("select r from ContentReport r where r.status = :status order by "
            + "case when r.reason in (com.educonnect.postservice.model.ReportReason.HARASSMENT, "
            + "com.educonnect.postservice.model.ReportReason.THREAT) then 0 else 1 end, r.createdAt asc")
    Page<ContentReport> findByStatusSensitiveFirst(@Param("status") ContentReport.Status status, Pageable pageable);
}
