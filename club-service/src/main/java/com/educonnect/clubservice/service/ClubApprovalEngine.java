package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAccess;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ClubApprovalEngine {

    private static final Logger log = LoggerFactory.getLogger(ClubApprovalEngine.class);

    private final ClubApprovalRequestRepository requestRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubLeadershipService leadershipService;
    private final ClubDecisionLog decisionLog;
    private final Map<ApprovalType, ApprovalHandler> handlers = new EnumMap<>(ApprovalType.class);
    private final Clock clock;

    ClubApprovalEngine(ClubApprovalRequestRepository requestRepository,
                       ClubRepository clubRepository,
                       ClubAuthorizationService clubAuthorizationService,
                       ClubLeadershipService leadershipService,
                       ClubDecisionLog decisionLog,
                       List<ApprovalHandler> handlerList) {
        this.requestRepository = requestRepository;
        this.clubRepository = clubRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.leadershipService = leadershipService;
        this.decisionLog = decisionLog;
        handlerList.forEach(handler -> handlers.put(handler.type(), handler));
        this.clock = Clock.systemUTC();
    }

    public ClubApprovalRequest submit(Club club, ClubApprovalRequest request) {
        ApprovalHandler handler = handler(request.getType());
        ClubAccess preparer = clubAuthorizationService.accessOf(club, request.getPreparedBy());
        if (handler.needsPresidentApproval(request, preparer)) {
            request.awaitPresident();
        }
        ClubApprovalRequest saved = requestRepository.save(request);
        decisionLog.record(saved, DecisionAction.SUBMITTED, saved.getPreparedBy(), saved.getNote());
        log.info("Club approval request submitted: clubId={}, requestId={}, type={}, status={}",
                club.getId(), saved.getId(), saved.getType(), saved.getStatus());
        if (saved.getStatus() == ApprovalStatus.PENDING_ADVISOR && !handler.needsAdvisorApproval()) {
            return conclude(club, saved, handler, saved.getPreparedBy());
        }
        handler.onAwaitingDecision(club, saved, currentDecider(club, saved, handler));
        return saved;
    }

    public ClubApprovalRequest approve(UUID requestId, UUID userId) {
        ClubApprovalRequest request = findPending(requestId);
        Club club = findClub(request.getClubId());
        ApprovalHandler handler = handler(request.getType());
        if (request.getStatus() == ApprovalStatus.PENDING_PRESIDENT) {
            requirePresident(club, userId);
            request.approveAsPresident(userId, clock.instant());
            if (!handler.needsAdvisorApproval()) {
                return conclude(club, request, handler, userId);
            }
            requestRepository.save(request);
            decisionLog.record(request, DecisionAction.PRESIDENT_APPROVED, userId, null);
            handler.onAwaitingDecision(club, request, currentDecider(club, request, handler));
            return request;
        }
        requireAdvisorStage(club, request, handler, userId);
        return conclude(club, request, handler, userId);
    }

    private ClubApprovalRequest conclude(Club club, ClubApprovalRequest request, ApprovalHandler handler, UUID approverId) {
        handler.apply(club, request, approverId);
        request.conclude(ApprovalStatus.APPROVED, approverId, null, clock.instant());
        requestRepository.save(request);
        decisionLog.record(request, DecisionAction.APPROVED, approverId, null);
        log.info("Club approval request approved: clubId={}, requestId={}, type={}", club.getId(), request.getId(), request.getType());
        return request;
    }

    public ClubApprovalRequest reject(UUID requestId, UUID userId, String reason) {
        ClubApprovalRequest request = findPending(requestId);
        Club club = findClub(request.getClubId());
        ApprovalHandler handler = handler(request.getType());
        if (request.getStatus() == ApprovalStatus.PENDING_PRESIDENT) {
            requirePresident(club, userId);
        } else {
            requireAdvisorStage(club, request, handler, userId);
        }
        request.conclude(ApprovalStatus.REJECTED, userId, reason, clock.instant());
        requestRepository.save(request);
        decisionLog.record(request, DecisionAction.REJECTED, userId, reason);
        log.info("Club approval request rejected: clubId={}, requestId={}, type={}", club.getId(), requestId, request.getType());
        handler.onRejected(club, request);
        return request;
    }

    public ClubApprovalRequest withdraw(UUID requestId, UUID userId) {
        ClubApprovalRequest request = findPending(requestId);
        Club club = findClub(request.getClubId());
        boolean preparer = userId.equals(request.getPreparedBy());
        if (!preparer && !clubAuthorizationService.accessOf(club, userId).actingPresident()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Talebi yalnızca hazırlayan kişi veya başkan geri çekebilir.");
        }
        request.conclude(ApprovalStatus.WITHDRAWN, userId, null, clock.instant());
        requestRepository.save(request);
        decisionLog.record(request, DecisionAction.WITHDRAWN, userId, null);
        handler(request.getType()).onWithdrawn(club, request);
        return request;
    }

    @Transactional(readOnly = true)
    public ClubApprovalRequest find(UUID clubId, UUID requestId) {
        return requestRepository.findById(requestId)
                .filter(request -> request.getClubId().equals(clubId))
                .orElseThrow(() -> new NotFoundException("APPROVAL_NOT_FOUND", "Talep bulunamadı"));
    }

    @Transactional(readOnly = true)
    public List<ClubApprovalRequest> requestsOf(UUID clubId, UUID userId) {
        findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_DECISIONS);
        return requestRepository.findByClubIdOrderByCreatedAtDesc(clubId);
    }

    @Transactional(readOnly = true)
    public List<ClubApprovalRequest> inboxOf(UUID userId) {
        List<ClubApprovalRequest> inbox = new ArrayList<>();
        List<UUID> presidedClubs = clubAuthorizationService.accessesOf(userId).stream()
                .filter(ClubAccess::actingPresident)
                .map(ClubAccess::clubId)
                .toList();
        if (!presidedClubs.isEmpty()) {
            inbox.addAll(requestRepository.findByClubIdInAndStatusOrderByCreatedAtAsc(presidedClubs, ApprovalStatus.PENDING_PRESIDENT));
        }
        List<UUID> advisedClubs = clubRepository.findByAcademicAdvisorId(userId).stream()
                .filter(club -> !club.isClosed())
                .map(Club::getId)
                .toList();
        if (!advisedClubs.isEmpty()) {
            requestRepository.findByClubIdInAndStatusOrderByCreatedAtAsc(advisedClubs, ApprovalStatus.PENDING_ADVISOR).stream()
                    .filter(request -> request.getType() != ApprovalType.ADVISOR_CHANGE)
                    .forEach(inbox::add);
        }
        inbox.addAll(requestRepository.findByTypeAndSubjectUserIdAndStatus(
                ApprovalType.ADVISOR_CHANGE, userId, ApprovalStatus.PENDING_ADVISOR));
        inbox.sort(Comparator.comparing(ClubApprovalRequest::getCreatedAt));
        return inbox;
    }

    private UUID currentDecider(Club club, ClubApprovalRequest request, ApprovalHandler handler) {
        if (request.getStatus() == ApprovalStatus.PENDING_PRESIDENT) {
            return leadershipService.currentLeaderOf(club.getId()).orElse(null);
        }
        return handler.advisorStageDecider(club, request);
    }

    private void requirePresident(Club club, UUID userId) {
        if (!clubAuthorizationService.accessOf(club, userId).has(ClubPermission.APPROVE_AS_PRESIDENT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu talep başkan onayı bekliyor.");
        }
    }

    private void requireAdvisorStage(Club club, ClubApprovalRequest request, ApprovalHandler handler, UUID userId) {
        UUID decider = handler.advisorStageDecider(club, request);
        if (decider == null) {
            throw new ConflictException("AWAITING_ADVISOR", "Kulübün danışmanı yok; talep yeni danışmanı bekliyor.");
        }
        if (!decider.equals(userId) || club.isClosed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu talep hakkında karar verme yetkiniz yok.");
        }
    }

    private ClubApprovalRequest findPending(UUID requestId) {
        ClubApprovalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("APPROVAL_NOT_FOUND", "Talep bulunamadı"));
        if (!request.isPending()) {
            throw new ConflictException("REQUEST_DECIDED", "Bu talep zaten işlenmiş.");
        }
        return request;
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }

    private ApprovalHandler handler(ApprovalType type) {
        ApprovalHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("No approval handler for " + type);
        }
        return handler;
    }
}
