package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.model.MembershipEndReason;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class MembershipRenewalService {

    private static final Logger log = LoggerFactory.getLogger(MembershipRenewalService.class);

    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final MembershipTerms membershipTerms;
    private final ClubDecisionLog decisionLog;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubNotificationPublisher notificationPublisher;

    public MembershipRenewalService(ClubMembershipRepository membershipRepository,
                                    ClubRepository clubRepository,
                                    MembershipTerms membershipTerms,
                                    ClubDecisionLog decisionLog,
                                    ClubCacheEvictor cacheEvictor,
                                    ClubNotificationPublisher notificationPublisher) {
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.membershipTerms = membershipTerms;
        this.decisionLog = decisionLog;
        this.cacheEvictor = cacheEvictor;
        this.notificationPublisher = notificationPublisher;
    }

    public ClubMembership renew(UUID clubId, UUID studentId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulübün üyeliği yenilenemez.");
        }
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .orElseThrow(() -> new NotFoundException("MEMBERSHIP_NOT_FOUND", "Bu kulüpte üyeliğiniz yok."));
        if (membership.isActive()) {
            if (membership.getClubRole() != ClubPosition.MEMBER || !membershipTerms.inRenewalWindow(membership.getValidUntil())) {
                throw new ConflictException("MEMBERSHIP_VALID", "Üyeliğiniz geçerli; yenileme dönemi henüz açılmadı.");
            }
            membership.setValidUntil(membershipTerms.validUntilFor(membership.getValidUntil().plusDays(1)));
        } else if (membership.getEndReason() == MembershipEndReason.EXPIRED) {
            membership.reactivate(membershipTerms.currentValidUntil(), LocalDateTime.now());
        } else {
            throw new ConflictException("MEMBERSHIP_ENDED", "Bu kulübe yeniden katılmak için üyelik başvurusu yapın.");
        }
        membershipRepository.save(membership);
        cacheEvictor.evictUser(studentId);
        decisionLog.record(clubId, DecisionAction.MEMBERSHIP_RENEWED, studentId, studentId,
                String.valueOf(membership.getValidUntil()));
        return membership;
    }

    @Scheduled(cron = "${educonnect.club.membership.expiry-cron:0 30 3 * * *}")
    public void expireMemberships() {
        int expired = expireDue();
        if (expired > 0) {
            log.info("Expired {} club memberships at the end of the academic year", expired);
        }
    }

    public int expireDue() {
        List<ClubMembership> due = membershipRepository.findByIsActiveAndClubRoleAndValidUntilBefore(
                true, ClubPosition.MEMBER, membershipTerms.today());
        if (due.isEmpty()) {
            return 0;
        }
        Map<UUID, Club> clubs = clubRepository.findAllById(due.stream().map(ClubMembership::getClubId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Club::getId, Function.identity()));
        int expired = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ClubMembership membership : due) {
            Club club = clubs.get(membership.getClubId());
            if (club == null || club.isClosed()) {
                continue;
            }
            membership.end(MembershipEndReason.EXPIRED, now);
            membershipRepository.save(membership);
            cacheEvictor.evictUser(membership.getStudentId());
            notificationPublisher.notifyUser(membership.getStudentId(), club, "Kulüp üyeliğinin süresi doldu",
                    "\"" + club.getName() + "\" kulübündeki üyeliğiniz akademik yıl sonunda sona erdi. "
                            + "Yeni akademik yıl için üyeliğinizi tek tıkla yenileyebilirsiniz.");
            expired++;
        }
        return expired;
    }
}
