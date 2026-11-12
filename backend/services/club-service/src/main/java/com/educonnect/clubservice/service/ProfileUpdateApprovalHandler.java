package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubProfileChange;
import com.educonnect.clubservice.repository.ClubProfileChangeRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAccess;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ProfileUpdateApprovalHandler implements ApprovalHandler {

    private static final String SUBJECT = "Kulüp bilgisi değişikliği";

    private final ClubRepository clubRepository;
    private final ClubProfileChangeRepository profileChangeRepository;
    private final ClubNotificationPublisher notificationPublisher;
    private final ClubCatalogEvents catalogEvents;

    ProfileUpdateApprovalHandler(ClubRepository clubRepository,
                                 ClubProfileChangeRepository profileChangeRepository,
                                 ClubNotificationPublisher notificationPublisher,
                                 ClubCatalogEvents catalogEvents) {
        this.clubRepository = clubRepository;
        this.profileChangeRepository = profileChangeRepository;
        this.notificationPublisher = notificationPublisher;
        this.catalogEvents = catalogEvents;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CLUB_PROFILE_UPDATE;
    }

    @Override
    public boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return !preparer.actingPresident();
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        if (deciderId != null) {
            notificationPublisher.notifyUser(deciderId, club, SUBJECT,
                    "\"" + club.getName() + "\" kulübünün bilgilerinde değişiklik önerildi; onayınız bekleniyor.");
        }
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubProfileChange change = profileChangeRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalStateException("Profile change missing for request " + request.getId()));
        club.setAbout(change.getAbout());
        club.setProfile(change.getProfile());
        clubRepository.save(club);
        catalogEvents.changed(club);
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT,
                "\"" + club.getName() + "\" kulübünün bilgi değişikliği onaylandı ve yayına alındı.");
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String message = "\"" + club.getName() + "\" kulübünün bilgi değişikliği talebi reddedildi.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, message);
    }
}
