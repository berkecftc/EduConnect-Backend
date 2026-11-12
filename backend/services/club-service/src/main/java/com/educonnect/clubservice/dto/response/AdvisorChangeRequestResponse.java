package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ClubApprovalRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdvisorChangeRequestResponse(UUID id,
                                           UUID clubId,
                                           String clubName,
                                           UUID proposedAdvisorId,
                                           String proposedAdvisorName,
                                           UUID requestedBy,
                                           String requestedByName,
                                           String message,
                                           ApprovalStatus status,
                                           String rejectionReason,
                                           Instant createdAt,
                                           Instant decidedAt) {

    public static AdvisorChangeRequestResponse of(ClubApprovalRequest request, String clubName, Map<UUID, String> names) {
        return new AdvisorChangeRequestResponse(request.getId(), request.getClubId(), clubName,
                request.getSubjectUserId(), names.get(request.getSubjectUserId()),
                request.getPreparedBy(), names.get(request.getPreparedBy()), request.getNote(), request.getStatus(),
                request.getRejectionReason(), request.getCreatedAt(), request.getDecidedAt());
    }
}
