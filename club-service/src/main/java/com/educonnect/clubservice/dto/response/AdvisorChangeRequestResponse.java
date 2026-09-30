package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.AdvisorChangeRequest;
import com.educonnect.clubservice.model.AdvisorChangeRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record AdvisorChangeRequestResponse(UUID id,
                                           UUID clubId,
                                           String clubName,
                                           UUID proposedAdvisorId,
                                           UUID previousAdvisorId,
                                           UUID requestedBy,
                                           String message,
                                           AdvisorChangeRequestStatus status,
                                           String rejectionReason,
                                           Instant createdAt,
                                           Instant decidedAt) {

    public static AdvisorChangeRequestResponse of(AdvisorChangeRequest request, String clubName) {
        return new AdvisorChangeRequestResponse(request.getId(), request.getClubId(), clubName,
                request.getProposedAdvisorId(), request.getPreviousAdvisorId(), request.getRequestedBy(),
                request.getMessage(), request.getStatus(), request.getRejectionReason(),
                request.getCreatedAt(), request.getDecidedAt());
    }
}
