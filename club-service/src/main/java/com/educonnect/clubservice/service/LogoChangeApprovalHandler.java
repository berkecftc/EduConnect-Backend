package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubProfileChange;
import com.educonnect.clubservice.repository.ClubProfileChangeRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAccess;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
class LogoChangeApprovalHandler implements ApprovalHandler {

    private static final String SUBJECT = "Kulüp logosu değişikliği";

    private final ClubRepository clubRepository;
    private final ClubProfileChangeRepository profileChangeRepository;
    private final ClubNotificationPublisher notificationPublisher;
    private final MinioService minioService;
    private final ClubCacheEvictor cacheEvictor;

    LogoChangeApprovalHandler(ClubRepository clubRepository,
                              ClubProfileChangeRepository profileChangeRepository,
                              ClubNotificationPublisher notificationPublisher,
                              MinioService minioService,
                              ClubCacheEvictor cacheEvictor) {
        this.clubRepository = clubRepository;
        this.profileChangeRepository = profileChangeRepository;
        this.notificationPublisher = notificationPublisher;
        this.minioService = minioService;
        this.cacheEvictor = cacheEvictor;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CLUB_LOGO_CHANGE;
    }

    @Override
    public boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return !preparer.actingPresident();
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        if (deciderId != null) {
            notificationPublisher.notifyUser(deciderId, club, SUBJECT,
                    "\"" + club.getName() + "\" kulübü için yeni logo önerildi; onayınız bekleniyor.");
        }
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        String previous = club.getLogoUrl();
        String proposed = proposedLogo(request);
        club.setLogoUrl(proposed);
        clubRepository.save(club);
        if (previous != null && !Objects.equals(previous, proposed)) {
            minioService.deleteAfterCommit(previous);
        }
        cacheEvictor.evictAllMemberships();
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT,
                "\"" + club.getName() + "\" kulübünün yeni logosu onaylandı ve yayına alındı.");
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        minioService.deleteAfterCommit(proposedLogo(request));
        String message = "\"" + club.getName() + "\" kulübünün logo değişikliği talebi reddedildi.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, message);
    }

    @Override
    public void onWithdrawn(Club club, ClubApprovalRequest request) {
        minioService.deleteAfterCommit(proposedLogo(request));
    }

    private String proposedLogo(ClubApprovalRequest request) {
        return profileChangeRepository.findById(request.getId())
                .map(ClubProfileChange::getLogoUrl)
                .orElse(null);
    }
}
