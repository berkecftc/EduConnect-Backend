package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.message.RoleChangeNotificationMessage.Status;
import com.educonnect.clubservice.dto.message.RoleChangeNotificationMessage.Type;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
class RoleChangeApprovalHandler implements ApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(RoleChangeApprovalHandler.class);

    private final ClubMembershipRepository membershipRepository;
    private final ClubPositionRules positionRules;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubLeadershipService leadershipService;
    private final RoleChangeNotifier notifier;
    private final RoleChangeUserNames userNames;
    private final MembershipTerms membershipTerms;

    RoleChangeApprovalHandler(ClubMembershipRepository membershipRepository,
                              ClubPositionRules positionRules,
                              ClubCacheEvictor cacheEvictor,
                              ClubManagementStatusPublisher managementStatusPublisher,
                              ClubLeadershipService leadershipService,
                              RoleChangeNotifier notifier,
                              RoleChangeUserNames userNames,
                              MembershipTerms membershipTerms) {
        this.membershipRepository = membershipRepository;
        this.positionRules = positionRules;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.leadershipService = leadershipService;
        this.notifier = notifier;
        this.userNames = userNames;
        this.membershipTerms = membershipTerms;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.ROLE_CHANGE;
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        String message = request.getRequestedPosition() == ClubPosition.MEMBER
                ? "Yeni bir görevden alma talebi onayınızı bekliyor."
                : "Yeni görev değişikliği talebi onayınızı bekliyor.";
        notifier.send(deciderId, club, request.getSubjectUserId(), userNames.nameOf(request.getSubjectUserId()),
                RoleChangeNotifier.previousRoleOf(request), request.getRequestedPosition(),
                Status.PENDING, message, Type.ROLE_CHANGE_REQUEST);
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubMembership membership = validateStillValid(request);
        ClubPosition previousRole = membership.getClubRole();
        ClubPosition newRole = request.getRequestedPosition();
        membership.setClubRole(newRole);
        membership.setActive(true);
        if (newRole == ClubPosition.MEMBER) {
            membership.setTermEndDate(LocalDateTime.now());
            membership.setValidUntil(membershipTerms.currentValidUntil());
        } else {
            membership.setTermStartDate(LocalDateTime.now());
            membership.setTermEndDate(null);
        }
        membershipRepository.save(membership);
        cacheEvictor.evictUser(request.getSubjectUserId());
        managementStatusPublisher.publishCurrentStatus(request.getSubjectUserId());
        log.info("Role change applied: requestId={}, studentId={}, from={}, to={}",
                request.getId(), request.getSubjectUserId(), previousRole, newRole);

        String studentName = userNames.nameOf(request.getSubjectUserId());
        String studentMessage = newRole == ClubPosition.MEMBER
                ? "Kulüpteki göreviniz sonlandırıldı."
                : "Görev değişikliği talebiniz onaylandı! Yeni göreviniz: " + newRole.displayName();
        notifier.send(request.getSubjectUserId(), club, request.getSubjectUserId(), studentName,
                previousRole, newRole, Status.APPROVED, studentMessage, Type.ROLE_CHANGE_APPROVED);
        leadershipService.currentLeaderOf(club.getId())
                .filter(leaderId -> !leaderId.equals(request.getSubjectUserId()))
                .ifPresent(leaderId -> notifier.send(leaderId, club, request.getSubjectUserId(), studentName,
                        previousRole, newRole, Status.APPROVED, studentName + " için görev değişikliği onaylandı.",
                        Type.ROLE_CHANGE_APPROVED));
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        String studentName = userNames.nameOf(request.getSubjectUserId());
        String rejectionMessage = "Görev değişikliği talebiniz reddedildi.";
        if (request.getRejectionReason() != null) {
            rejectionMessage += " Neden: " + request.getRejectionReason();
        }
        ClubPosition previousRole = RoleChangeNotifier.previousRoleOf(request);
        notifier.send(request.getSubjectUserId(), club, request.getSubjectUserId(), studentName,
                previousRole, request.getRequestedPosition(), Status.REJECTED, rejectionMessage, Type.ROLE_CHANGE_REJECTED);
        leadershipService.currentLeaderOf(club.getId())
                .ifPresent(leaderId -> notifier.send(leaderId, club, request.getSubjectUserId(), studentName,
                        previousRole, request.getRequestedPosition(), Status.REJECTED,
                        studentName + " için görev değişikliği talebi reddedildi.", Type.ROLE_CHANGE_REJECTED));
    }

    private ClubMembership validateStillValid(ClubApprovalRequest request) {
        ClubMembership membership = membershipRepository
                .findByClubIdAndStudentId(request.getClubId(), request.getSubjectUserId())
                .filter(ClubMembership::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Öğrenci artık kulüp üyesi değil. Talep geçersiz."));
        if (request.getCurrentPosition() != null && membership.getClubRole() != request.getCurrentPosition()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Öğrencinin görevi talep oluşturulduktan sonra değişmiş. Talep geçersiz.");
        }
        ClubPosition requestedRole = request.getRequestedPosition();
        if (requestedRole.isManagement()) {
            positionRules.ensureCapacity(request.getClubId(), requestedRole, false);
            positionRules.ensureNoManagementPositionElsewhere(request.getSubjectUserId(), request.getClubId());
        }
        return membership;
    }
}
