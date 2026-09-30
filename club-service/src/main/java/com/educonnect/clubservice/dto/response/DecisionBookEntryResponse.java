package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubMeeting;
import com.educonnect.clubservice.model.ClubMeetingDecision;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record DecisionBookEntryResponse(int number,
                                        String label,
                                        String text,
                                        UUID meetingId,
                                        LocalDateTime meetingAt,
                                        Instant approvedAt) {

    public static DecisionBookEntryResponse of(ClubMeetingDecision decision, ClubMeeting meeting) {
        return new DecisionBookEntryResponse(decision.getDecisionNumber(),
                AcademicYears.label(decision.getAcademicYear()) + "/" + decision.getDecisionNumber(), decision.getText(),
                meeting.getId(), meeting.getMeetingAt(), meeting.getApprovedAt());
    }
}
