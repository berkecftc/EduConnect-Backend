package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.model.MembershipEndReason;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ClubAffiliationService {

    private static final Logger log = LoggerFactory.getLogger(ClubAffiliationService.class);

    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final ClubLeadershipService leadershipService;
    private final AdvisorChangeService advisorChangeService;
    private final ClubDecisionLog decisionLog;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final MembershipTerms membershipTerms;

    public ClubAffiliationService(ClubMembershipRepository membershipRepository,
                                  ClubRepository clubRepository,
                                  ClubLeadershipService leadershipService,
                                  AdvisorChangeService advisorChangeService,
                                  ClubDecisionLog decisionLog,
                                  ClubCacheEvictor cacheEvictor,
                                  ClubManagementStatusPublisher managementStatusPublisher,
                                  MembershipTerms membershipTerms) {
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.leadershipService = leadershipService;
        this.advisorChangeService = advisorChangeService;
        this.decisionLog = decisionLog;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.membershipTerms = membershipTerms;
    }

    @Transactional
    public void studentOnLeave(UUID studentId) {
        endMemberships(studentId, MembershipEndReason.FROZEN, DecisionAction.MEMBERSHIP_FROZEN);
    }

    @Transactional
    public void studentEnded(UUID studentId) {
        endMemberships(studentId, MembershipEndReason.AFFILIATION_ENDED, DecisionAction.MEMBERSHIP_ENDED_BY_STATUS);
        LocalDateTime now = LocalDateTime.now();
        for (ClubMembership frozen : membershipRepository.findByStudentId(studentId)) {
            if (!frozen.isActive() && frozen.getEndReason() == MembershipEndReason.FROZEN) {
                frozen.end(MembershipEndReason.AFFILIATION_ENDED, now);
                membershipRepository.save(frozen);
            }
        }
    }

    @Transactional
    public void studentResumed(UUID studentId) {
        LocalDateTime now = LocalDateTime.now();
        int resumed = 0;
        for (ClubMembership membership : membershipRepository.findByStudentId(studentId)) {
            if (membership.isActive() || membership.getEndReason() != MembershipEndReason.FROZEN) {
                continue;
            }
            Club club = clubRepository.findById(membership.getClubId()).orElse(null);
            if (club == null || club.isClosed()) {
                continue;
            }
            membership.reactivate(membershipTerms.currentValidUntil(), now);
            membershipRepository.save(membership);
            decisionLog.record(membership.getClubId(), DecisionAction.MEMBERSHIP_RESUMED, null, studentId, null);
            resumed++;
        }
        cacheEvictor.evictUser(studentId);
        log.info("Frozen memberships resumed: student={}, count={}", studentId, resumed);
    }

    @Transactional
    public void staffEnded(UUID staffId) {
        List<UUID> advised = new ArrayList<>();
        for (Club club : clubRepository.findByAcademicAdvisorId(staffId)) {
            if (club.isClosed()) {
                continue;
            }
            club.setAcademicAdvisorId(null);
            club.setStatus(ClubStatus.AWAITING_ADVISOR);
            clubRepository.save(club);
            advised.add(club.getId());
        }
        advisorChangeService.advisorLeft(advised);
        log.info("Advisor affiliation ended: staff={}, clubs={}", staffId, advised.size());
    }

    private void endMemberships(UUID studentId, MembershipEndReason reason, DecisionAction action) {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> presided = new ArrayList<>();
        boolean management = false;
        for (ClubMembership membership : membershipRepository.findByStudentId(studentId)) {
            if (!membership.isActive()) {
                continue;
            }
            if (membership.getClubRole() == ClubPosition.PRESIDENT) {
                presided.add(membership.getClubId());
            }
            management |= membership.getClubRole() != null && membership.getClubRole().isManagement();
            membership.end(reason, now);
            membershipRepository.save(membership);
            decisionLog.record(membership.getClubId(), action, null, studentId, null);
        }
        membershipRepository.flush();
        cacheEvictor.evictUser(studentId);
        presided.forEach(leadershipService::handlePresidencyVacancy);
        if (management) {
            managementStatusPublisher.publishCurrentStatus(studentId);
        }
        log.info("Memberships ended by student status: student={}, reason={}, presidencies={}", studentId, reason, presided.size());
    }
}
