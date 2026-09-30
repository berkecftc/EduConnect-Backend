package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ClubFinanceEntry;
import com.educonnect.clubservice.model.FinanceEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinanceEntryResponse(UUID id,
                                   FinanceEntryType type,
                                   BigDecimal amount,
                                   String description,
                                   LocalDate occurredOn,
                                   int academicYear,
                                   boolean hasDocument,
                                   String documentName,
                                   boolean budgetExceeded,
                                   UUID sponsorshipId,
                                   UUID preparedBy,
                                   Instant createdAt,
                                   Instant approvedAt,
                                   ApprovalStatus status) {

    public static FinanceEntryResponse of(ClubFinanceEntry entry, ApprovalStatus status) {
        if (entry == null) {
            return null;
        }
        return new FinanceEntryResponse(entry.getId(), entry.getType(), entry.getAmount(), entry.getDescription(),
                entry.getOccurredOn(), entry.getAcademicYear(), entry.getDocumentObject() != null, entry.getDocumentName(),
                entry.isBudgetExceeded(), entry.getSponsorshipId(), entry.getPreparedBy(), entry.getCreatedAt(),
                entry.getApprovedAt(), status);
    }
}
