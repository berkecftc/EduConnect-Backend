package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubPosition;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ApprovalRequestResponse(UUID id,
                                      UUID clubId,
                                      String clubName,
                                      ApprovalType type,
                                      ApprovalStatus status,
                                      UUID preparedBy,
                                      String preparedByName,
                                      UUID subjectUserId,
                                      String subjectUserName,
                                      ClubPosition currentPosition,
                                      ClubPosition requestedPosition,
                                      String note,
                                      String rejectionReason,
                                      String responseNote,
                                      Instant createdAt,
                                      UUID presidentDecidedBy,
                                      String presidentDecidedByName,
                                      Instant presidentDecidedAt,
                                      UUID decidedBy,
                                      String decidedByName,
                                      Instant decidedAt,
                                      ProfileChangeResponse profileChange,
                                      AnnouncementResponse announcement,
                                      BudgetResponse budget,
                                      FinanceEntryResponse financeEntry,
                                      SponsorshipResponse sponsorship,
                                      MeetingResponse meeting,
                                      ReportResponse report,
                                      ElectionResponse election) {

    public static ApprovalRequestResponse of(ClubApprovalRequest request, String clubName, ApprovalDetails details,
                                             Map<UUID, String> userNames) {
        ApprovalDetails extra = details != null ? details : ApprovalDetails.NONE;
        Map<UUID, String> names = userNames != null ? userNames : Map.of();
        return new ApprovalRequestResponse(request.getId(), request.getClubId(), clubName, request.getType(),
                request.getStatus(), request.getPreparedBy(), nameOf(names, request.getPreparedBy()),
                request.getSubjectUserId(), nameOf(names, request.getSubjectUserId()), request.getCurrentPosition(),
                request.getRequestedPosition(), request.getNote(), request.getRejectionReason(), request.getResponseNote(), request.getCreatedAt(),
                request.getPresidentDecidedBy(), nameOf(names, request.getPresidentDecidedBy()), request.getPresidentDecidedAt(),
                request.getDecidedBy(), nameOf(names, request.getDecidedBy()), request.getDecidedAt(), extra.profileChange(), extra.announcement(), extra.budget(), extra.financeEntry(),
                extra.sponsorship(), extra.meeting(), extra.report(), extra.election());
    }

    private static String nameOf(Map<UUID, String> names, UUID userId) {
        return userId != null ? names.get(userId) : null;
    }
}
