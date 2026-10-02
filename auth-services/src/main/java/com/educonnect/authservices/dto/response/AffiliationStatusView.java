package com.educonnect.authservices.dto.response;

import com.educonnect.authservices.models.AffiliationStatusChange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AffiliationStatusView(UUID userId, String studentStatus, String staffStatus, Instant closureDueAt,
                                    List<Change> history) {

    public record Change(String affiliation, String previousStatus, String status, LocalDate effectiveDate,
                         String reason, String changedBy, Instant changedAt) {

        public static Change of(AffiliationStatusChange change) {
            return new Change(change.getAffiliation(), change.getPreviousStatus(), change.getStatus(),
                    change.getEffectiveDate(), change.getReason(), change.getChangedBy(), change.getCreatedAt());
        }
    }
}
