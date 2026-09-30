package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.PositionEndReason;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.security.ClubAccess;
import com.educonnect.common.web.ConflictException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
class ResignationApprovalHandler implements ApprovalHandler {

    private final ClubMembershipRepository membershipRepository;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubLeadershipService leadershipService;
    private final ClubNotificationPublisher notificationPublisher;
    private final RoleChangeUserNames userNames;
    private final MembershipTerms membershipTerms;

    ResignationApprovalHandler(ClubMembershipRepository membershipRepository,
                               ClubCacheEvictor cacheEvictor,
                               ClubManagementStatusPublisher managementStatusPublisher,
                               ClubLeadershipService leadershipService,
                               ClubNotificationPublisher notificationPublisher,
                               RoleChangeUserNames userNames,
                               MembershipTerms membershipTerms) {
        this.membershipRepository = membershipRepository;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.leadershipService = leadershipService;
        this.notificationPublisher = notificationPublisher;
        this.userNames = userNames;
        this.membershipTerms = membershipTerms;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.RESIGNATION;
    }

    @Override
    public boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return !preparer.actingPresident();
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        notificationPublisher.notifyUser(deciderId, club, "Görevden istifa",
                userNames.nameOf(request.getSubjectUserId()) + " \"" + club.getName() + "\" kulübündeki "
                        + request.getCurrentPosition().displayName() + " görevinden istifa etmek istiyor; onayınız bekleniyor.");
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(club.getId(), request.getSubjectUserId())
                .filter(ClubMembership::isActive)
                .filter(found -> found.getClubRole() == request.getCurrentPosition())
                .orElseThrow(() -> new ConflictException("POSITION_CHANGED",
                        "Görevlinin görevi talep oluşturulduktan sonra değişmiş. Talep geçersiz."));
        boolean wasPresident = membership.getClubRole() == ClubPosition.PRESIDENT;
        membership.assignPosition(ClubPosition.MEMBER, LocalDateTime.now(), PositionEndReason.RESIGNED);
        membership.setValidUntil(membershipTerms.currentValidUntil());
        membershipRepository.save(membership);
        cacheEvictor.evictUser(request.getSubjectUserId());
        managementStatusPublisher.publishCurrentStatus(request.getSubjectUserId());
        notificationPublisher.notifyUser(request.getSubjectUserId(), club, "Görevden istifa",
                "\"" + club.getName() + "\" kulübündeki " + request.getCurrentPosition().displayName()
                        + " görevinden istifanız onaylandı. Kulübün üyesi olarak devam ediyorsunuz.");
        if (wasPresident) {
            leadershipService.handlePresidencyVacancy(club.getId());
        }
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String message = "\"" + club.getName() + "\" kulübündeki istifa talebiniz reddedildi.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getSubjectUserId(), club, "Görevden istifa", message);
    }
}
