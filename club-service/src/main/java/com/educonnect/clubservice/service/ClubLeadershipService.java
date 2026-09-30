package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ClubLeadershipService {

    private static final Logger log = LoggerFactory.getLogger(ClubLeadershipService.class);

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubPositionRules positionRules;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubNotificationPublisher notificationPublisher;
    private final ClubDecisionLog decisionLog;

    public ClubLeadershipService(ClubRepository clubRepository,
                                 ClubMembershipRepository membershipRepository,
                                 ClubAuthorizationService clubAuthorizationService,
                                 ClubPositionRules positionRules,
                                 ClubCacheEvictor cacheEvictor,
                                 ClubManagementStatusPublisher managementStatusPublisher,
                                 ClubNotificationPublisher notificationPublisher,
                                 ClubDecisionLog decisionLog) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.positionRules = positionRules;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.notificationPublisher = notificationPublisher;
        this.decisionLog = decisionLog;
    }

    public void handlePresidencyVacancy(UUID clubId) {
        Club club = clubRepository.findById(clubId).orElse(null);
        if (club == null || club.isClosed() || activePresident(clubId).isPresent()) {
            return;
        }
        Optional<ClubMembership> vicePresident = membershipRepository
                .findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.VICE_PRESIDENT, true).stream()
                .findFirst();
        if (vicePresident.isEmpty()) {
            log.info("Presidency vacant without vice president: clubId={}", clubId);
            decisionLog.record(clubId, DecisionAction.PRESIDENCY_VACANT, null, null, null);
            notificationPublisher.notifyAdvisor(club, "Kulüp başkansız kaldı",
                    "\"" + club.getName() + "\" kulübünün başkanı ve başkan yardımcısı yok. "
                            + "Danışman olarak kulübün aktif üyelerinden yeni başkanı atayabilirsiniz.");
            return;
        }
        ClubMembership successor = vicePresident.get();
        promote(successor);
        decisionLog.record(clubId, DecisionAction.VICE_PRESIDENT_PROMOTED, null, successor.getStudentId(), null);
        log.info("Vice president promoted to president: clubId={}, studentId={}", clubId, successor.getStudentId());
        notificationPublisher.notifyUser(successor.getStudentId(), club, "Kulüp başkanlığı",
                "\"" + club.getName() + "\" kulübünün başkanlığı boşaldığı için başkan yardımcılığından kulüp başkanlığına geçtiniz.");
        notificationPublisher.notifyAdvisor(club, "Kulüp başkanlığı",
                "\"" + club.getName() + "\" kulübünün başkanlığı boşaldı; başkan yardımcısı kulüp başkanı oldu. "
                        + "Başkan yardımcılığı görevi boş.");
    }

    public ClubMembership appointPresident(UUID clubId, UUID advisorId, UUID studentId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
        clubAuthorizationService.require(clubId, advisorId, ClubPermission.ADVISE);
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulübe başkan atanamaz.");
        }
        if (activePresident(clubId).isPresent()) {
            throw new ConflictException("PRESIDENT_EXISTS", "Kulübün zaten bir başkanı var.");
        }
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .filter(ClubMembership::isActive)
                .orElseThrow(() -> new NotFoundException("MEMBERSHIP_NOT_FOUND",
                        "Başkan olarak yalnızca kulübün aktif bir üyesi atanabilir."));
        positionRules.ensureNoManagementPositionElsewhere(studentId, clubId);
        boolean wasManagement = membership.getClubRole().isManagement();
        promote(membership);
        decisionLog.record(clubId, DecisionAction.PRESIDENT_APPOINTED, advisorId, studentId, null);
        if (!wasManagement) {
            managementStatusPublisher.publishCurrentStatus(studentId);
        }
        log.info("President appointed by advisor: clubId={}, studentId={}, advisorId={}", clubId, studentId, advisorId);
        notificationPublisher.notifyUser(studentId, club, "Kulüp başkanlığı",
                "\"" + club.getName() + "\" kulübünün danışmanı sizi kulüp başkanı olarak atadı.");
        return membership;
    }

    public Optional<UUID> currentLeaderOf(UUID clubId) {
        return activePresident(clubId)
                .or(() -> membershipRepository
                        .findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.VICE_PRESIDENT, true).stream()
                        .findFirst())
                .map(ClubMembership::getStudentId);
    }

    private void promote(ClubMembership membership) {
        membership.setClubRole(ClubPosition.PRESIDENT);
        membership.setTermStartDate(LocalDateTime.now());
        membership.setTermEndDate(null);
        membershipRepository.save(membership);
        cacheEvictor.evictUser(membership.getStudentId());
    }

    private Optional<ClubMembership> activePresident(UUID clubId) {
        return membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true).stream()
                .findFirst();
    }
}
