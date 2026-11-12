package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ClubClosureApprovalHandler implements ApprovalHandler {

    private final ClubClosureService closureService;
    private final ClubLeadershipService leadershipService;
    private final ClubNotificationPublisher notificationPublisher;

    ClubClosureApprovalHandler(ClubClosureService closureService,
                               ClubLeadershipService leadershipService,
                               ClubNotificationPublisher notificationPublisher) {
        this.closureService = closureService;
        this.leadershipService = leadershipService;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CLUB_CLOSURE;
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        notificationPublisher.notifyUser(deciderId, club, "Kulüp kapatma talebi",
                "\"" + club.getName() + "\" kulübünün başkanı kulübün kapatılmasını talep etti; onayınız bekleniyor. "
                        + "Gerekçe: " + request.getNote());
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        closureService.close(club, approverId, request.getNote(), request.getId());
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String message = "\"" + club.getName() + "\" kulübünün kapatma talebi danışman tarafından reddedildi.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        String finalMessage = message;
        leadershipService.currentLeaderOf(club.getId())
                .ifPresent(leaderId -> notificationPublisher.notifyUser(leaderId, club, "Kulüp kapatma talebi", finalMessage));
    }
}
