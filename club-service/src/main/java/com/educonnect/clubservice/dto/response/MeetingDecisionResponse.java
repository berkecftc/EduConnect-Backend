package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubMeetingDecision;

import java.util.UUID;

public record MeetingDecisionResponse(UUID id,
                                      int order,
                                      String text,
                                      Integer number,
                                      String label) {

    public static MeetingDecisionResponse of(ClubMeetingDecision decision) {
        Integer number = decision.getDecisionNumber();
        return new MeetingDecisionResponse(decision.getId(), decision.getItemOrder(), decision.getText(), number,
                number != null ? AcademicYears.label(decision.getAcademicYear()) + "/" + number : null);
    }
}
