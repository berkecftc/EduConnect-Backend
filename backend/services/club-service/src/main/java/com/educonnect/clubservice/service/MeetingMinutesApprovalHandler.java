package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.response.AcademicYears;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMeeting;
import com.educonnect.clubservice.model.ClubMeetingDecision;
import com.educonnect.clubservice.repository.ClubMeetingDecisionRepository;
import com.educonnect.clubservice.repository.ClubMeetingRepository;
import com.educonnect.clubservice.security.ClubAccess;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Component
class MeetingMinutesApprovalHandler implements ApprovalHandler {

    private static final String SUBJECT = "Yönetim kurulu tutanağı";

    private final ClubMeetingRepository meetingRepository;
    private final ClubMeetingDecisionRepository decisionRepository;
    private final ClubNotificationPublisher notificationPublisher;
    private final Clock clock = Clock.systemUTC();

    MeetingMinutesApprovalHandler(ClubMeetingRepository meetingRepository,
                                  ClubMeetingDecisionRepository decisionRepository,
                                  ClubNotificationPublisher notificationPublisher) {
        this.meetingRepository = meetingRepository;
        this.decisionRepository = decisionRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CLUB_MEETING_MINUTES;
    }

    @Override
    public boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return !preparer.actingPresident();
    }

    @Override
    public boolean needsAdvisorApproval(ClubApprovalRequest request) {
        return false;
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        if (deciderId != null) {
            notificationPublisher.notifyUser(deciderId, club, SUBJECT,
                    "\"" + club.getName() + "\" kulübünün yönetim kurulu tutanağı onayınızı bekliyor.");
        }
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubMeeting meeting = meetingOf(request);
        meeting.approve(clock.instant());
        meetingRepository.save(meeting);
        List<ClubMeetingDecision> decisions = decisionRepository.findByMeetingIdOrderByItemOrder(meeting.getId());
        int next = decisionRepository.lastNumber(club.getId(), meeting.getAcademicYear());
        for (ClubMeetingDecision decision : decisions) {
            decision.number(++next);
        }
        decisionRepository.saveAll(decisions);
        String message = "\"" + club.getName() + "\" kulübünün yönetim kurulu tutanağı onaylandı";
        if (!decisions.isEmpty()) {
            message += "; " + decisions.size() + " karar " + AcademicYears.label(meeting.getAcademicYear())
                    + " karar defterine işlendi";
        }
        notificationPublisher.notifyAdvisor(club, SUBJECT, message + ".");
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, message + ".");
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String message = "\"" + club.getName() + "\" kulübünün yönetim kurulu tutanağı reddedildi.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, message);
    }

    private ClubMeeting meetingOf(ClubApprovalRequest request) {
        return meetingRepository.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalStateException("Meeting missing for request " + request.getId()));
    }
}
