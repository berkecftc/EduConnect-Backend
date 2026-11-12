package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubReport;
import com.educonnect.clubservice.model.ReportStatus;
import com.educonnect.clubservice.model.ReportType;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(UUID id,
                             UUID clubId,
                             ReportType type,
                             int academicYear,
                             String academicYearLabel,
                             ReportStatus status,
                             String body,
                             String financeNote,
                             String recommendations,
                             ReportSnapshotResponse snapshot,
                             UUID requestId,
                             UUID createdBy,
                             UUID updatedBy,
                             Instant createdAt,
                             Instant updatedAt,
                             Instant submittedAt,
                             Instant approvedAt) {

    public static ReportResponse of(ClubReport report) {
        if (report == null) {
            return null;
        }
        return new ReportResponse(report.getId(), report.getClubId(), report.getType(), report.getAcademicYear(),
                AcademicYears.label(report.getAcademicYear()), report.getStatus(), report.getBody(), report.getFinanceNote(),
                report.getRecommendations(), ReportSnapshotResponse.of(report.getSnapshot()), report.getRequestId(),
                report.getCreatedBy(), report.getUpdatedBy(), report.getCreatedAt(), report.getUpdatedAt(),
                report.getSubmittedAt(), report.getApprovedAt());
    }
}
