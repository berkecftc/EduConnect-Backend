package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubCreationRequestStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ClubCreationRequestResponse(UUID id,
                                          String clubName,
                                          String about,
                                          UUID requestingStudentId,
                                          UUID suggestedAdvisorId,
                                          ClubCreationRequestStatus status,
                                          LocalDateTime requestDate,
                                          String rejectionReason,
                                          LocalDateTime processedAt,
                                          UUID processedBy,
                                          UUID clubId,
                                          List<FounderResponse> founders) {

    public static ClubCreationRequestResponse from(ClubCreationRequest request, List<FounderResponse> founders) {
        return new ClubCreationRequestResponse(request.getId(), request.getClubName(), request.getAbout(),
                request.getRequestingStudentId(), request.getSuggestedAdvisorId(), request.getStatus(),
                request.getRequestDate(), request.getRejectionReason(), request.getProcessedAt(),
                request.getProcessedBy(), request.getClubId(), founders);
    }
}
