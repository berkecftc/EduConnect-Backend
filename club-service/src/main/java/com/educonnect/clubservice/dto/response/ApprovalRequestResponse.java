package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubPosition;

import java.time.Instant;
import java.util.UUID;

public record ApprovalRequestResponse(UUID id,
                                      UUID clubId,
                                      String clubName,
                                      ApprovalType type,
                                      ApprovalStatus status,
                                      UUID preparedBy,
                                      UUID subjectUserId,
                                      ClubPosition currentPosition,
                                      ClubPosition requestedPosition,
                                      String note,
                                      String rejectionReason,
                                      String responseNote,
                                      Instant createdAt,
                                      UUID presidentDecidedBy,
                                      Instant presidentDecidedAt,
                                      UUID decidedBy,
                                      Instant decidedAt,
                                      ProfileChangeResponse profileChange,
                                      AnnouncementResponse announcement,
                                      BudgetResponse budget,
                                      FinanceEntryResponse financeEntry,
                                      SponsorshipResponse sponsorship) {

    public static ApprovalRequestResponse of(ClubApprovalRequest request, String clubName, ApprovalDetails details) {
        ApprovalDetails extra = details != null ? details : ApprovalDetails.NONE;
        return new ApprovalRequestResponse(request.getId(), request.getClubId(), clubName, request.getType(),
                request.getStatus(), request.getPreparedBy(), request.getSubjectUserId(), request.getCurrentPosition(),
                request.getRequestedPosition(), request.getNote(), request.getRejectionReason(), request.getResponseNote(), request.getCreatedAt(),
                request.getPresidentDecidedBy(), request.getPresidentDecidedAt(), request.getDecidedBy(),
                request.getDecidedAt(), extra.profileChange(), extra.announcement(), extra.budget(), extra.financeEntry(),
                extra.sponsorship());
    }
}
