package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.repository.ClubRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class AdvisorChangeApprovalHandler implements ApprovalHandler {

    private final ClubRepository clubRepository;
    private final ClubLeadershipService leadershipService;
    private final ClubNotificationPublisher notificationPublisher;

    AdvisorChangeApprovalHandler(ClubRepository clubRepository,
                                 ClubLeadershipService leadershipService,
                                 ClubNotificationPublisher notificationPublisher) {
        this.clubRepository = clubRepository;
        this.leadershipService = leadershipService;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.ADVISOR_CHANGE;
    }

    @Override
    public UUID advisorStageDecider(Club club, ClubApprovalRequest request) {
        return request.getSubjectUserId();
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        notificationPublisher.notifyUser(deciderId, club, "Kulüp danışmanlığı teklifi",
                "\"" + club.getName() + "\" kulübü sizi danışman akademisyen olarak öneriyor. "
                        + "Teklifi kabul edebilir veya reddedebilirsiniz.");
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        UUID previousAdvisorId = club.getAcademicAdvisorId();
        club.setAcademicAdvisorId(approverId);
        club.setStatus(ClubStatus.ACTIVE);
        clubRepository.save(club);
        if (previousAdvisorId != null && !previousAdvisorId.equals(approverId)) {
            notificationPublisher.notifyUser(previousAdvisorId, club, "Kulüp danışmanlığı",
                    "\"" + club.getName() + "\" kulübünün danışmanlığı yeni danışmana devredildi. Danışmanlık göreviniz sona erdi.");
        }
        notifyLeader(club, "\"" + club.getName() + "\" kulübü için önerdiğiniz akademisyen danışmanlığı kabul etti.");
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String message = "\"" + club.getName() + "\" kulübü için önerdiğiniz akademisyen danışmanlığı kabul etmedi.";
        if (request.getRejectionReason() != null && !request.getRejectionReason().isBlank()) {
            message += " Neden: " + request.getRejectionReason();
        }
        notifyLeader(club, message);
    }

    private void notifyLeader(Club club, String message) {
        leadershipService.currentLeaderOf(club.getId())
                .ifPresent(leaderId -> notificationPublisher.notifyUser(leaderId, club, "Kulüp danışmanlığı", message));
    }
}
