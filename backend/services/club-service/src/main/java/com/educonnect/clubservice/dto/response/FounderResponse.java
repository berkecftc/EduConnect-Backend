package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubFounder;
import com.educonnect.clubservice.model.FounderStatus;

import java.time.Instant;
import java.util.UUID;

public record FounderResponse(UUID studentId,
                              String firstName,
                              String lastName,
                              FounderStatus status,
                              Instant respondedAt) {

    public static FounderResponse of(ClubFounder founder, UserSummary user) {
        return new FounderResponse(founder.getStudentId(), user != null ? user.getFirstName() : null,
                user != null ? user.getLastName() : null, founder.getStatus(), founder.getRespondedAt());
    }
}
