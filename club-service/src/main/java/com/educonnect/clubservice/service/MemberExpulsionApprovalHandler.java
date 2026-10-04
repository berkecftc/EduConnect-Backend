package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.MembershipEndReason;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.common.web.ConflictException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
class MemberExpulsionApprovalHandler implements ApprovalHandler {

    private final ClubMembershipRepository membershipRepository;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubLeadershipService leadershipService;
    private final ClubNotificationPublisher notificationPublisher;
    private final RoleChangeUserNames userNames;

    MemberExpulsionApprovalHandler(ClubMembershipRepository membershipRepository,
                                   ClubCacheEvictor cacheEvictor,
                                   ClubManagementStatusPublisher managementStatusPublisher,
                                   ClubLeadershipService leadershipService,
                                   ClubNotificationPublisher notificationPublisher,
                                   RoleChangeUserNames userNames) {
        this.membershipRepository = membershipRepository;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.leadershipService = leadershipService;
        this.notificationPublisher = notificationPublisher;
        this.userNames = userNames;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.MEMBER_EXPULSION;
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        notificationPublisher.notifyUser(deciderId, club, "Üyelikten çıkarma talebi",
                "\"" + club.getName() + "\" kulübünün başkanı " + userNames.nameOf(request.getSubjectUserId())
                        + " adlı üyenin kulüpten çıkarılmasını talep etti; onayınız bekleniyor. Gerekçe: " + request.getNote());
        notificationPublisher.notifyUser(request.getSubjectUserId(), club, "Üyelikten çıkarma talebi",
                "\"" + club.getName() + "\" kulübünden çıkarılmanız için danışmana bir talep iletildi. Gerekçe: "
                        + request.getNote() + ". Danışman karar verene kadar savunmanızı ekleyebilirsiniz.");
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(club.getId(), request.getSubjectUserId())
                .filter(ClubMembership::isActive)
                .orElseThrow(() -> new ConflictException("NOT_A_MEMBER", "Öğrenci artık kulübün aktif üyesi değil. Talep geçersiz."));
        if (membership.getClubRole() == ClubPosition.PRESIDENT) {
            throw new ConflictException("PRESIDENT_EXPULSION", "Kulüp başkanı bu yolla çıkarılamaz.");
        }
        boolean wasManagement = membership.getClubRole().isManagement();
        membership.end(MembershipEndReason.EXPELLED, Instant.now());
        membershipRepository.save(membership);
        cacheEvictor.evictUser(request.getSubjectUserId());
        if (wasManagement) {
            managementStatusPublisher.publishCurrentStatus(request.getSubjectUserId());
        }
        notificationPublisher.notifyUser(request.getSubjectUserId(), club, "Üyelikten çıkarma",
                "\"" + club.getName() + "\" kulübünden danışman kararıyla çıkarıldınız. Gerekçe: " + request.getNote());
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        notificationPublisher.notifyUser(request.getSubjectUserId(), club, "Üyelikten çıkarma talebi",
                "\"" + club.getName() + "\" kulübünden çıkarılmanıza ilişkin talep reddedildi; üyeliğiniz devam ediyor.");
        leadershipService.currentLeaderOf(club.getId())
                .ifPresent(leaderId -> notificationPublisher.notifyUser(leaderId, club, "Üyelikten çıkarma talebi",
                        userNames.nameOf(request.getSubjectUserId()) + " için çıkarma talebi danışman tarafından reddedildi."));
    }
}
