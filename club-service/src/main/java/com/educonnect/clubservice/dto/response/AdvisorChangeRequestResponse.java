package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ClubApprovalRequest;

import java.time.Instant;
import java.util.UUID;

public record AdvisorChangeRequestResponse(UUID id,
                                           UUID clubId,
                                           String clubName,
                                           UUID proposedAdvisorId,
                                           UUID requestedBy,
                                           String message,
                                           ApprovalStatus status,
                                           String rejectionReason,
                                           Instant createdAt,
                                           Instant decidedAt) {

    public static AdvisorChangeRequestResponse of(ClubApprovalRequest request, String clubName) {
        return new AdvisorChangeRequestResponse(request.getId(), request.getClubId(), clubName,
                request.getSubjectUserId(), request.getPreparedBy(), request.getNote(), request.getStatus(),
                request.getRejectionReason(), request.getCreatedAt(), request.getDecidedAt());
    }
}
