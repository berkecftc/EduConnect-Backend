package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ClubMeeting;
import com.educonnect.clubservice.model.ClubMeetingDecision;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MeetingResponse(UUID id,
                              LocalDateTime meetingAt,
                              int academicYear,
                              String academicYearLabel,
                              String location,
                              String agenda,
                              String minutes,
                              Set<UUID> attendeeIds,
                              int boardSize,
                              boolean quorumMet,
                              List<MeetingDecisionResponse> decisions,
                              UUID preparedBy,
                              Instant createdAt,
                              Instant approvedAt,
                              ApprovalStatus status) {

    public static MeetingResponse of(ClubMeeting meeting, List<ClubMeetingDecision> decisions, ApprovalStatus status) {
        if (meeting == null) {
            return null;
        }
        return new MeetingResponse(meeting.getId(), meeting.getMeetingAt(), meeting.getAcademicYear(),
                AcademicYears.label(meeting.getAcademicYear()), meeting.getLocation(), meeting.getAgenda(),
                meeting.getMinutes(), meeting.getAttendeeIds(), meeting.getBoardSize(), meeting.isQuorumMet(),
                decisions.stream().map(MeetingDecisionResponse::of).toList(), meeting.getPreparedBy(),
                meeting.getCreatedAt(), meeting.getApprovedAt(), status);
    }
}
