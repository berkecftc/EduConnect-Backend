package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.model.ClubCreationRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClubCreationRequestResponse(UUID id,
                                          String clubName,
                                          String about,
                                          UUID requestingStudentId,
                                          String requestingStudentName,
                                          UUID suggestedAdvisorId,
                                          String suggestedAdvisorName,
                                          ClubCreationRequestStatus status,
                                          Instant requestDate,
                                          String rejectionReason,
                                          Instant processedAt,
                                          UUID processedBy,
                                          UUID clubId,
                                          List<FounderResponse> founders) {

    public static ClubCreationRequestResponse from(ClubCreationRequest request, List<FounderResponse> founders,
                                                   String requestingStudentName, String suggestedAdvisorName) {
        return new ClubCreationRequestResponse(request.getId(), request.getClubName(), request.getAbout(),
                request.getRequestingStudentId(), requestingStudentName,
                request.getSuggestedAdvisorId(), suggestedAdvisorName, request.getStatus(),
                request.getRequestDate(), request.getRejectionReason(), request.getProcessedAt(),
                request.getProcessedBy(), request.getClubId(), founders);
    }
}
