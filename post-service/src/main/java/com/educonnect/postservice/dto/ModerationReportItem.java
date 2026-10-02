package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.ContentReport;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.ReportReason;

import java.time.Instant;
import java.util.UUID;

public record ModerationReportItem(
        UUID id,
        ModerationTarget targetType,
        UUID targetId,
        UUID postId,
        ReportReason reason,
        String reasonLabel,
        boolean sensitive,
        String details,
        UUID reporterId,
        ContentReport.Status status,
        long openReportsOnTarget,
        String targetStatus,
        String targetContent,
        Instant createdAt
) {}
