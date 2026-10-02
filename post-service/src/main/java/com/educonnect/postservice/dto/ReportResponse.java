package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.ContentReport;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.ReportReason;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        ModerationTarget targetType,
        UUID targetId,
        UUID postId,
        ReportReason reason,
        String reasonLabel,
        boolean sensitive,
        ContentReport.Status status,
        Instant createdAt,
        String supportMessage
) {

    public static final String SUPPORT_MESSAGE = "Bildirimin öncelikli olarak bir moderatöre iletildi. "
            + "Acil bir tehlike varsa kampüs güvenliğine veya 112'ye başvur; konuşmak istersen "
            + "üniversitenin psikolojik danışmanlık birimi sana destek olabilir.";

    public static ReportResponse from(ContentReport report) {
        boolean sensitive = report.getReason().sensitive();
        return new ReportResponse(report.getId(), report.getTargetType(), report.getTargetId(), report.getPostId(),
                report.getReason(), report.getReason().label(), sensitive, report.getStatus(), report.getCreatedAt(),
                sensitive ? SUPPORT_MESSAGE : null);
    }
}
