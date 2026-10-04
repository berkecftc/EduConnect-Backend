package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.ClubUpdateMessage;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubMembershipRequest;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.model.PositionEndReason;
import com.educonnect.clubservice.model.MembershipRequestStatus;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubMembershipRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.educonnect.common.messaging.notification.NotificationCategory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

@Service
@Transactional
public class ClubClosureService {

    private static final Logger log = LoggerFactory.getLogger(ClubClosureService.class);
    private static final String ROUTING_KEY_CLUB_DELETED = "club.deleted";
    private static final String CLOSED_REASON = "Kulüp kapatıldı.";

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubMembershipRequestRepository membershipRequestRepository;
    private final ClubApprovalRequestRepository approvalRequestRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubDecisionLog decisionLog;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubNotificationPublisher notificationPublisher;
    private final OutboxPublisher outboxPublisher;
    private final PositionHistoryRecorder positionHistory;
    private final Clock clock;

    public ClubClosureService(ClubRepository clubRepository,
                              ClubMembershipRepository membershipRepository,
                              ClubMembershipRequestRepository membershipRequestRepository,
                              ClubApprovalRequestRepository approvalRequestRepository,
                              ClubAuthorizationService clubAuthorizationService,
                              ClubDecisionLog decisionLog,
                              ClubCacheEvictor cacheEvictor,
                              ClubManagementStatusPublisher managementStatusPublisher,
                              ClubNotificationPublisher notificationPublisher,
                              OutboxPublisher outboxPublisher,
                              PositionHistoryRecorder positionHistory) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRequestRepository = membershipRequestRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.decisionLog = decisionLog;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.notificationPublisher = notificationPublisher;
        this.outboxPublisher = outboxPublisher;
        this.positionHistory = positionHistory;
        this.clock = Clock.systemUTC();
    }

    public Club closeByAdvisor(UUID clubId, UUID advisorId, String reason) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kulüp zaten kapatılmış.");
        }
        clubAuthorizationService.require(clubId, advisorId, ClubPermission.ADVISE);
        close(club, advisorId, reason, null);
        return club;
    }

    void close(Club club, UUID closedBy, String reason, UUID closingRequestId) {
        UUID clubId = club.getId();
        club.close(closedBy, reason, clock.instant());
        clubRepository.save(club);
        closePendingRequests(club, closedBy, closingRequestId);
        positionHistory.closeAll(clubId, Instant.now(), PositionEndReason.CLUB_CLOSED);
        decisionLog.record(clubId, DecisionAction.CLUB_CLOSED, closedBy, null, reason);

        List<ClubMembership> activeMembers = membershipRepository.findByClubId(clubId).stream()
                .filter(ClubMembership::isActive)
                .toList();
        for (ClubMembership membership : activeMembers) {
            cacheEvictor.evictUser(membership.getStudentId());
            if (membership.getClubRole().isManagement()) {
                managementStatusPublisher.publishCurrentStatus(membership.getStudentId());
            }
        }
        notificationPublisher.notifyUsers(activeMembers.stream().map(ClubMembership::getStudentId).toList(), club,
                NotificationCategory.CLUB_MANAGEMENT, ClubNotificationPublisher.TYPE_NOTICE, "Kulüp kapatıldı",
                "\"" + club.getName() + "\" kulübü danışman kararıyla kapatıldı. "
                        + "Üyelik ve görev geçmişiniz korunuyor; kulübün gelecekteki etkinlikleri iptal edildi. Neden: " + reason);
        outboxPublisher.publish(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, ROUTING_KEY_CLUB_DELETED,
                new ClubUpdateMessage(clubId, club.getName(), null));
        log.info("Club closed: clubId={}, members={}", clubId, activeMembers.size());
    }

    private void closePendingRequests(Club club, UUID closedBy, UUID closingRequestId) {
        UUID clubId = club.getId();
        Instant now = Instant.now(clock);
        List<UUID> applicants = new ArrayList<>();
        for (ClubMembershipRequest request : membershipRequestRepository.findByClubIdAndStatus(clubId, MembershipRequestStatus.PENDING)) {
            request.setStatus(MembershipRequestStatus.REJECTED);
            request.setRejectionReason(CLOSED_REASON);
            request.setProcessedBy(closedBy);
            request.setProcessedDate(now);
            membershipRequestRepository.save(request);
            applicants.add(request.getStudentId());
        }
        notificationPublisher.notifyUsers(applicants, club, NotificationCategory.CLUB_MANAGEMENT,
                ClubMembershipRequestService.TYPE_MEMBERSHIP, "Üyelik başvurunuz kapandı",
                "\"" + club.getName() + "\" kulübü kapatıldığı için üyelik başvurunuz sonuçlanmadan kapandı.");
        for (ClubApprovalRequest request : approvalRequestRepository.findByClubIdAndStatusIn(clubId, ApprovalStatus.PENDING)) {
            if (request.getId().equals(closingRequestId)) {
                continue;
            }
            request.conclude(ApprovalStatus.REJECTED, closedBy, CLOSED_REASON, clock.instant());
            approvalRequestRepository.save(request);
            decisionLog.record(request, DecisionAction.REJECTED, closedBy, CLOSED_REASON);
        }
    }
}
