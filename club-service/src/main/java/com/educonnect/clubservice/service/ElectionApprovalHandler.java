package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubElection;
import com.educonnect.clubservice.model.ClubElectionCandidate;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.model.PositionEndReason;
import com.educonnect.clubservice.repository.ClubElectionCandidateRepository;
import com.educonnect.clubservice.repository.ClubElectionRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.security.ClubAccess;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.common.web.ConflictException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
class ElectionApprovalHandler implements ApprovalHandler {

    private static final String SUBJECT = "Genel kurul seçimi";

    private final ClubElectionRepository electionRepository;
    private final ClubElectionCandidateRepository candidateRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final MembershipTerms membershipTerms;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubNotificationPublisher notificationPublisher;
    private final ClubDecisionLog decisionLog;
    private final Clock clock = Clock.systemUTC();

    ElectionApprovalHandler(ClubElectionRepository electionRepository,
                            ClubElectionCandidateRepository candidateRepository,
                            ClubMembershipRepository membershipRepository,
                            ClubAuthorizationService clubAuthorizationService,
                            MembershipTerms membershipTerms,
                            ClubCacheEvictor cacheEvictor,
                            ClubManagementStatusPublisher managementStatusPublisher,
                            ClubNotificationPublisher notificationPublisher,
                            ClubDecisionLog decisionLog) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.membershipRepository = membershipRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.membershipTerms = membershipTerms;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.notificationPublisher = notificationPublisher;
        this.decisionLog = decisionLog;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CLUB_ELECTION;
    }

    @Override
    public boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return !preparer.actingPresident();
    }

    @Override
    public void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId) {
        if (deciderId != null) {
            notificationPublisher.notifyUser(deciderId, club, SUBJECT,
                    "\"" + club.getName() + "\" kulübünün seçim sonuçları onayınızı bekliyor.");
        }
    }

    @Override
    public void apply(Club club, ClubApprovalRequest request, UUID approverId) {
        ClubElection election = electionOf(request);
        List<ClubElectionCandidate> winners = candidateRepository.findByElectionIdOrderByCreatedAt(election.getId()).stream()
                .filter(ClubElectionCandidate::isElected)
                .toList();
        for (ClubElectionCandidate winner : winners) {
            clubAuthorizationService.activeManagementPositionOf(winner.getStudentId())
                    .filter(membership -> !membership.getClubId().equals(club.getId()))
                    .ifPresent(membership -> {
                        throw new ConflictException("WINNER_HAS_POSITION",
                                "Seçilen adaylardan biri başka bir kulüpte yönetim görevi aldı; devir yapılamaz.");
                    });
        }
        Instant now = Instant.now(clock);
        Set<UUID> affected = new LinkedHashSet<>();
        for (ClubMembership membership : membershipRepository.findByClubId(club.getId())) {
            if (membership.isActive() && membership.getClubRole().isManagement()) {
                membership.assignPosition(ClubPosition.MEMBER, now, PositionEndReason.HANDOVER);
                membership.setValidUntil(membershipTerms.currentValidUntil());
                membershipRepository.save(membership);
                affected.add(membership.getStudentId());
            }
        }
        for (ClubElectionCandidate winner : winners) {
            membershipRepository.findByClubIdAndStudentId(club.getId(), winner.getStudentId())
                    .filter(ClubMembership::isActive)
                    .ifPresent(membership -> {
                        membership.assignPosition(winner.getBallot().position(), now, PositionEndReason.HANDOVER);
                        membershipRepository.save(membership);
                        affected.add(membership.getStudentId());
                        notificationPublisher.notifyUser(membership.getStudentId(), club, SUBJECT,
                                "\"" + club.getName() + "\" kulübünde " + winner.getBallot().position().displayName()
                                        + " olarak seçildiniz; görev devri tamamlandı.");
                    });
        }
        affected.forEach(studentId -> {
            cacheEvictor.evictUser(studentId);
            managementStatusPublisher.publishCurrentStatus(studentId);
        });
        election.complete(clock.instant());
        electionRepository.save(election);
        decisionLog.record(club.getId(), DecisionAction.BOARD_HANDOVER, approverId, null,
                winners.size() + " görev seçimle devredildi");
    }

    @Override
    public void onRejected(Club club, ClubApprovalRequest request) {
        cancel(request);
        String message = "\"" + club.getName() + "\" kulübünün seçim sonuçları onaylanmadı; seçim geçersiz sayıldı.";
        if (request.getRejectionReason() != null) {
            message += " Neden: " + request.getRejectionReason();
        }
        notificationPublisher.notifyUser(request.getPreparedBy(), club, SUBJECT, message);
    }

    @Override
    public void onWithdrawn(Club club, ClubApprovalRequest request) {
        cancel(request);
    }

    private void cancel(ClubApprovalRequest request) {
        ClubElection election = electionOf(request);
        election.cancel(clock.instant());
        electionRepository.save(election);
    }

    private ClubElection electionOf(ClubApprovalRequest request) {
        return electionRepository.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalStateException("Election missing for request " + request.getId()));
    }
}
