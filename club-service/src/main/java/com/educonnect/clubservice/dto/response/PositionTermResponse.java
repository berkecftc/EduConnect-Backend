package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubPositionTerm;
import com.educonnect.clubservice.model.PositionEndReason;

import java.time.Instant;
import java.util.UUID;

public record PositionTermResponse(UUID clubId,
                                   String clubName,
                                   UUID studentId,
                                   String firstName,
                                   String lastName,
                                   String position,
                                   String positionName,
                                   Instant startedAt,
                                   Instant endedAt,
                                   PositionEndReason endReason) {

    public static PositionTermResponse of(ClubPositionTerm term, String clubName, UserSummary user) {
        ClubPosition position = term.getPosition();
        return new PositionTermResponse(term.getClubId(), clubName, term.getStudentId(),
                user != null ? user.getFirstName() : null, user != null ? user.getLastName() : null,
                position.apiName(), position.displayName(), term.getStartedAt(), term.getEndedAt(), term.getEndReason());
    }
}
