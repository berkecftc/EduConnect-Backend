package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.request.AdvisorChangeProposal;
import com.educonnect.clubservice.dto.response.AdvisorChangeRequestResponse;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdvisorChangeService {

    private static final Logger log = LoggerFactory.getLogger(AdvisorChangeService.class);

    private final ClubApprovalRequestRepository requestRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final AdvisorDirectory advisorDirectory;
    private final ClubLeadershipService leadershipService;
    private final ClubNotificationPublisher notificationPublisher;
    private final ClubApprovalEngine approvalEngine;
    private final ClubDecisionLog decisionLog;
    private final ClubCatalogEvents catalogEvents;

    public AdvisorChangeService(ClubApprovalRequestRepository requestRepository,
                                ClubRepository clubRepository,
                                ClubAuthorizationService clubAuthorizationService,
                                AdvisorDirectory advisorDirectory,
                                ClubLeadershipService leadershipService,
                                ClubNotificationPublisher notificationPublisher,
                                ClubApprovalEngine approvalEngine,
                                ClubDecisionLog decisionLog,
                                ClubCatalogEvents catalogEvents) {
        this.requestRepository = requestRepository;
        this.clubRepository = clubRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.advisorDirectory = advisorDirectory;
        this.leadershipService = leadershipService;
        this.notificationPublisher = notificationPublisher;
        this.approvalEngine = approvalEngine;
        this.decisionLog = decisionLog;
        this.catalogEvents = catalogEvents;
    }

    public AdvisorChangeRequestResponse propose(UUID clubId, UUID requesterId, AdvisorChangeProposal proposal) {
        Club club = findClub(clubId);
        clubAuthorizationService.require(clubId, requesterId, ClubPermission.PROPOSE_ADVISOR_CHANGE);
        UUID proposedAdvisorId = proposal.proposedAdvisorId();
        if (proposedAdvisorId.equals(club.getAcademicAdvisorId())) {
            throw new BadRequestException("SAME_ADVISOR", "Önerilen akademisyen zaten kulübün danışmanı.");
        }
        if (proposedAdvisorId.equals(requesterId)) {
            throw new BadRequestException("INVALID_ADVISOR", "Talep eden kişi danışman olarak önerilemez.");
        }
        advisorDirectory.requireAcademician(proposedAdvisorId);
        if (requestRepository.existsByClubIdAndTypeAndStatusIn(clubId, ApprovalType.ADVISOR_CHANGE, ApprovalStatus.PENDING)) {
            throw new ConflictException("ADVISOR_CHANGE_PENDING", "Kulübün bekleyen bir danışman değişikliği talebi var.");
        }
        ClubApprovalRequest request = approvalEngine.submit(club, new ClubApprovalRequest(clubId,
                ApprovalType.ADVISOR_CHANGE, requesterId, proposedAdvisorId, null, null, proposal.message(), Instant.now()));
        return AdvisorChangeRequestResponse.of(request, club.getName());
    }

    @Transactional(readOnly = true)
    public List<AdvisorChangeRequestResponse> getClubRequests(UUID clubId, UUID userId) {
        Club club = findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_DECISIONS);
        return requestRepository.findByClubIdAndTypeOrderByCreatedAtDesc(clubId, ApprovalType.ADVISOR_CHANGE).stream()
                .map(request -> AdvisorChangeRequestResponse.of(request, club.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdvisorChangeRequestResponse> getPendingForAdvisor(UUID advisorId) {
        List<ClubApprovalRequest> requests = requestRepository.findByTypeAndSubjectUserIdAndStatus(
                ApprovalType.ADVISOR_CHANGE, advisorId, ApprovalStatus.PENDING_ADVISOR);
        Map<UUID, Club> clubs = clubsById(requests.stream().map(ClubApprovalRequest::getClubId).toList());
        return requests.stream()
                .map(request -> AdvisorChangeRequestResponse.of(request,
                        clubs.containsKey(request.getClubId()) ? clubs.get(request.getClubId()).getName() : null))
                .toList();
    }

    public AdvisorChangeRequestResponse accept(UUID requestId, UUID advisorId) {
        ClubApprovalRequest request = approvalEngine.approve(advisorChangeRequest(requestId).getId(), advisorId);
        return AdvisorChangeRequestResponse.of(request, findClub(request.getClubId()).getName());
    }

    public AdvisorChangeRequestResponse reject(UUID requestId, UUID advisorId, String reason) {
        ClubApprovalRequest request = approvalEngine.reject(advisorChangeRequest(requestId).getId(), advisorId, reason);
        return AdvisorChangeRequestResponse.of(request, findClub(request.getClubId()).getName());
    }

    public void cancel(UUID clubId, UUID requestId, UUID userId) {
        ClubApprovalRequest request = approvalEngine.find(clubId, requestId);
        if (request.getType() != ApprovalType.ADVISOR_CHANGE) {
            throw new NotFoundException("ADVISOR_CHANGE_NOT_FOUND", "Danışman değişikliği talebi bulunamadı.");
        }
        approvalEngine.withdraw(requestId, userId);
    }

    public void resign(UUID clubId, UUID advisorId, String reason) {
        Club club = findClub(clubId);
        clubAuthorizationService.require(clubId, advisorId, ClubPermission.ADVISE);
        club.setAcademicAdvisorId(null);
        club.setStatus(ClubStatus.AWAITING_ADVISOR);
        clubRepository.save(club);
        catalogEvents.changed(club);
        decisionLog.record(clubId, DecisionAction.ADVISOR_RESIGNED, advisorId, advisorId, reason);
        log.info("Advisor resigned: clubId={}", clubId);
        String message = "\"" + club.getName() + "\" kulübünün danışmanı görevini bıraktı. "
                + "Kulüp yeni bir danışman kabul edene kadar danışman onayı gereken işler bekleyecek; yeni danışman önerebilirsiniz.";
        if (reason != null && !reason.isBlank()) {
            message += " Neden: " + reason;
        }
        notifyLeader(club, "Kulüp danışmanı ayrıldı", message);
    }

    public void advisorLeft(Collection<UUID> clubIds) {
        for (Club club : clubRepository.findAllById(clubIds)) {
            if (club.isClosed()) {
                continue;
            }
            decisionLog.record(club.getId(), DecisionAction.ADVISOR_LEFT, null, null, null);
            notifyLeader(club, "Kulüp danışmanı ayrıldı",
                    "\"" + club.getName() + "\" kulübünün danışmanının hesabı kapandı. "
                            + "Kulüp yeni bir danışman kabul edene kadar danışman onayı gereken işler bekleyecek; yeni danışman önerebilirsiniz.");
        }
    }

    private ClubApprovalRequest advisorChangeRequest(UUID requestId) {
        return requestRepository.findById(requestId)
                .filter(request -> request.getType() == ApprovalType.ADVISOR_CHANGE)
                .orElseThrow(() -> new NotFoundException("ADVISOR_CHANGE_NOT_FOUND", "Danışman değişikliği talebi bulunamadı."));
    }

    private void notifyLeader(Club club, String subject, String message) {
        leadershipService.currentLeaderOf(club.getId())
                .ifPresent(leaderId -> notificationPublisher.notifyUser(leaderId, club, subject, message));
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }

    private Map<UUID, Club> clubsById(List<UUID> clubIds) {
        if (clubIds.isEmpty()) {
            return Map.of();
        }
        return clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, Function.identity()));
    }
}
