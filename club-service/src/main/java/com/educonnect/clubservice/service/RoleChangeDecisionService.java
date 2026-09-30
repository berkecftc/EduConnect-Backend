package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.message.RoleChangeNotificationMessage.Status;
import com.educonnect.clubservice.dto.message.RoleChangeNotificationMessage.Type;
import com.educonnect.clubservice.dto.request.RejectRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.response.RoleChangeRequestDTO;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleChangeDecisionService {

    private static final Logger log = LoggerFactory.getLogger(RoleChangeDecisionService.class);

    private final ClubApprovalRequestRepository approvalRequestRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final RoleChangeNotifier notifier;
    private final RoleChangeUserNames userNames;
    private final RoleChangeRequestMapper mapper;
    private final ClubLeadershipService leadershipService;
    private final ClubApprovalEngine approvalEngine;
    private final ClubDecisionLog decisionLog;

    public RoleChangeDecisionService(ClubApprovalRequestRepository approvalRequestRepository,
                                     ClubMembershipRepository membershipRepository,
                                     ClubRepository clubRepository,
                                     ClubAuthorizationService clubAuthorizationService,
                                     ClubCacheEvictor cacheEvictor,
                                     ClubManagementStatusPublisher managementStatusPublisher,
                                     RoleChangeNotifier notifier,
                                     RoleChangeUserNames userNames,
                                     RoleChangeRequestMapper mapper,
                                     ClubLeadershipService leadershipService,
                                     ClubApprovalEngine approvalEngine,
                                     ClubDecisionLog decisionLog) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.notifier = notifier;
        this.userNames = userNames;
        this.mapper = mapper;
        this.leadershipService = leadershipService;
        this.approvalEngine = approvalEngine;
        this.decisionLog = decisionLog;
    }

    @Transactional(readOnly = true)
    public List<RoleChangeRequestDTO> getPendingRequestsForAdvisor(UUID advisorId) {
        List<Club> advisorClubs = clubRepository.findByAcademicAdvisorId(advisorId);
        if (advisorClubs.isEmpty()) {
            return List.of();
        }
        Map<UUID, Club> clubsById = advisorClubs.stream().collect(Collectors.toMap(Club::getId, Function.identity()));
        List<ClubApprovalRequest> pendingRequests = approvalRequestRepository.findByClubIdInAndTypeAndStatus(
                clubsById.keySet(), ApprovalType.ROLE_CHANGE, ApprovalStatus.PENDING_ADVISOR);
        return mapper.toDtos(pendingRequests, request -> clubsById.get(request.getClubId()));
    }

    public RoleChangeRequestDTO approveRoleChangeRequest(UUID requestId, UUID advisorId) {
        requireRoleChange(requestId);
        ClubApprovalRequest request = approvalEngine.approve(requestId, advisorId);
        return mapper.toDto(request, clubRepository.findById(request.getClubId()).orElse(null));
    }

    public RoleChangeRequestDTO rejectRoleChangeRequest(UUID requestId, UUID advisorId, RejectRoleChangeRequestDTO dto) {
        requireRoleChange(requestId);
        String reason = dto != null ? dto.getRejectionReason() : null;
        ClubApprovalRequest request = approvalEngine.reject(requestId, advisorId, reason);
        return mapper.toDto(request, clubRepository.findById(request.getClubId()).orElse(null));
    }

    public void removePresidentByAdvisor(UUID clubId, UUID advisorId, String reason) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));
        clubAuthorizationService.require(clubId, advisorId, ClubPermission.ADVISE);

        ClubMembership president = membershipRepository
                .findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulübün aktif başkanı yok."));

        president.setClubRole(ClubPosition.MEMBER);
        president.setTermEndDate(LocalDateTime.now());
        membershipRepository.save(president);
        cacheEvictor.evictUser(president.getStudentId());
        managementStatusPublisher.publishCurrentStatus(president.getStudentId());
        decisionLog.record(clubId, DecisionAction.PRESIDENT_REMOVED, advisorId, president.getStudentId(), reason);

        log.info("President removed by advisor: clubId={}, studentId={}, advisorId={}",
                clubId, president.getStudentId(), advisorId);

        String studentName = userNames.nameOf(president.getStudentId());
        String message = "Kulüp başkanlığı göreviniz danışman kararıyla sonlandırıldı.";
        if (reason != null && !reason.isBlank()) {
            message += " Neden: " + reason;
        }
        notifier.send(president.getStudentId(), club, president.getStudentId(), studentName,
                ClubPosition.PRESIDENT, ClubPosition.MEMBER, Status.APPROVED, message, Type.ROLE_REVOKED);
        leadershipService.handlePresidencyVacancy(clubId);
    }

    @Transactional(readOnly = true)
    public long getPendingRequestCountForClub(UUID clubId, UUID advisorId) {
        clubAuthorizationService.require(clubId, advisorId, ClubPermission.ADVISE);
        return approvalRequestRepository.countByClubIdAndTypeAndStatusIn(clubId, ApprovalType.ROLE_CHANGE,
                ApprovalStatus.PENDING);
    }

    private void requireRoleChange(UUID requestId) {
        approvalRequestRepository.findById(requestId)
                .filter(request -> request.getType() == ApprovalType.ROLE_CHANGE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talep bulunamadı"));
    }
}
