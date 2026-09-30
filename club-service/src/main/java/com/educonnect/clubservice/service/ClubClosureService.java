package com.educonnect.clubservice.service;

import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.ClubUpdateMessage;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubMembershipRequest;
import com.educonnect.clubservice.model.MembershipRequestStatus;
import com.educonnect.clubservice.model.RoleChangeRequest;
import com.educonnect.clubservice.model.RoleChangeRequestStatus;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubMembershipRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.repository.RoleChangeRequestRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ClubClosureService {

    private static final Logger log = LoggerFactory.getLogger(ClubClosureService.class);
    private static final String ROUTING_KEY_CLUB_DELETED = "club.deleted";
    private static final String CLOSED_REASON = "Kulüp kapatıldı.";

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubMembershipRequestRepository membershipRequestRepository;
    private final RoleChangeRequestRepository roleChangeRequestRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final AdvisorChangeService advisorChangeService;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubNotificationPublisher notificationPublisher;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    public ClubClosureService(ClubRepository clubRepository,
                              ClubMembershipRepository membershipRepository,
                              ClubMembershipRequestRepository membershipRequestRepository,
                              RoleChangeRequestRepository roleChangeRequestRepository,
                              ClubAuthorizationService clubAuthorizationService,
                              AdvisorChangeService advisorChangeService,
                              ClubCacheEvictor cacheEvictor,
                              ClubManagementStatusPublisher managementStatusPublisher,
                              ClubNotificationPublisher notificationPublisher,
                              OutboxPublisher outboxPublisher) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRequestRepository = membershipRequestRepository;
        this.roleChangeRequestRepository = roleChangeRequestRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.advisorChangeService = advisorChangeService;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.notificationPublisher = notificationPublisher;
        this.outboxPublisher = outboxPublisher;
        this.clock = Clock.systemUTC();
    }

    public Club closeByAdvisor(UUID clubId, UUID advisorId, String reason) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kulüp zaten kapatılmış.");
        }
        clubAuthorizationService.require(clubId, advisorId, ClubPermission.ADVISE);

        club.close(advisorId, reason, clock.instant());
        clubRepository.save(club);
        closePendingRequests(clubId, advisorId);
        advisorChangeService.cancelPendingForClub(clubId);

        List<ClubMembership> activeMembers = membershipRepository.findByClubId(clubId).stream()
                .filter(ClubMembership::isActive)
                .toList();
        for (ClubMembership membership : activeMembers) {
            cacheEvictor.evictUser(membership.getStudentId());
            if (membership.getClubRole().isManagement()) {
                managementStatusPublisher.publishCurrentStatus(membership.getStudentId());
            }
            notificationPublisher.notifyUser(membership.getStudentId(), club, "Kulüp kapatıldı",
                    "\"" + club.getName() + "\" kulübü danışman kararıyla kapatıldı. "
                            + "Üyelik ve görev geçmişiniz korunuyor; kulübün gelecekteki etkinlikleri iptal edildi. Neden: " + reason);
        }
        outboxPublisher.publish(ClubRabbitMQConfig.CLUB_EXCHANGE_NAME, ROUTING_KEY_CLUB_DELETED,
                new ClubUpdateMessage(clubId, club.getName(), null));
        log.info("Club closed by advisor: clubId={}, members={}", clubId, activeMembers.size());
        return club;
    }

    private void closePendingRequests(UUID clubId, UUID advisorId) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (ClubMembershipRequest request : membershipRequestRepository.findByClubIdAndStatus(clubId, MembershipRequestStatus.PENDING)) {
            request.setStatus(MembershipRequestStatus.REJECTED);
            request.setRejectionReason(CLOSED_REASON);
            request.setProcessedBy(advisorId);
            request.setProcessedDate(now);
            membershipRequestRepository.save(request);
        }
        for (RoleChangeRequest request : roleChangeRequestRepository.findByClubIdAndStatus(clubId, RoleChangeRequestStatus.PENDING)) {
            request.setStatus(RoleChangeRequestStatus.REJECTED);
            request.setRejectionReason(CLOSED_REASON);
            request.setProcessedBy(advisorId);
            request.setProcessedAt(now);
            roleChangeRequestRepository.save(request);
        }
    }
}
