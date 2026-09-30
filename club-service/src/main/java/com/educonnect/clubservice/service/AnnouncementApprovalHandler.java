package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubAnnouncement;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.repository.ClubAnnouncementRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.security.ClubAccess;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
class AnnouncementApprovalHandler implements ApprovalHandler {

    private static final String SUBJECT = "Kulüp duyurusu";

    private final ClubAnnouncementRepository announcementRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubNotificationPublisher notificationPublisher;
    private final Clock clock = Clock.systemUTC();

    AnnouncementApprovalHandler(ClubAnnouncementRepository announcementRepository,
                                ClubMembershipRepository membershipRepository,
                                ClubNotificationPublisher notificationPublisher) {
        this.announcementRepository = announcementRepository;
        this.membershipRepository = membershipRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CLUB_ANNOUNCEMENT;
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
                    "\"" + club.getName() + "\" kulübü için yeni bir duyuru hazırlandı; onayınız bekleniyor: "
                            + announcementOf(request).getTitle());
        }
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubAnnouncement announcement = announcementOf(request);
        announcement.publish(clock.instant());
        announcementRepository.save(announcement);
        membershipRepository.findByClubId(club.getId()).stream()
                .filter(ClubMembership::isActive)
                .map(ClubMembership::getStudentId)
                .forEach(memberId -> notificationPublisher.notifyUser(memberId, club, SUBJECT,
                        "\"" + club.getName() + "\" kulübünden yeni duyuru: " + announcement.getTitle()));
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String message = "\"" + club.getName() + "\" kulübü için hazırladığınız duyuru yayımlanmadı: "
                + announcementOf(request).getTitle() + ".";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, message);
    }

    private ClubAnnouncement announcementOf(ClubApprovalRequest request) {
        return announcementRepository.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalStateException("Announcement missing for request " + request.getId()));
    }
}
