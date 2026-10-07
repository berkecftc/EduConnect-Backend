package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.response.ApprovalDetails;
import com.educonnect.clubservice.client.UserLookup;
import com.educonnect.clubservice.dto.response.ApprovalRequestResponse;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubDecisionLogEntry;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.repository.ClubApprovalRequestRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
public class ClubGovernanceService {
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubApprovalRequestRepository requestRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubApprovalEngine approvalEngine;
    private final ClubDecisionLog decisionLog;
    private final ApprovalDetailsLoader detailsLoader;
    private final UserLookup userLookup;

    public ClubGovernanceService(ClubRepository clubRepository,
                                 ClubMembershipRepository membershipRepository,
                                 ClubApprovalRequestRepository requestRepository,
                                 ClubAuthorizationService clubAuthorizationService,
                                 ClubApprovalEngine approvalEngine,
                                 ClubDecisionLog decisionLog,
                                 ApprovalDetailsLoader detailsLoader,
                                 UserLookup userLookup) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.requestRepository = requestRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.approvalEngine = approvalEngine;
        this.decisionLog = decisionLog;
        this.detailsLoader = detailsLoader;
        this.userLookup = userLookup;
    }

    public ClubApprovalRequest resign(UUID clubId, UUID userId, String note) {
        Club club = findClub(clubId);
        if (club.isClosed()) {
            throw new ConflictException("CLUB_CLOSED", "Kapatılmış kulüpte görevden istifa edilemez.");
        }
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, userId)
                .filter(ClubMembership::isActive)
                .filter(found -> found.getClubRole().isManagement())
                .orElseThrow(() -> new BadRequestException("NO_POSITION",
                        "Yalnızca yönetim görevi olan üyeler görevden istifa edebilir."));
        if (requestRepository.existsByClubIdAndTypeAndSubjectUserIdAndStatusIn(clubId, ApprovalType.RESIGNATION, userId,
                ApprovalStatus.PENDING)) {
            throw new ConflictException("RESIGNATION_PENDING", "Bekleyen bir istifa talebiniz var.");
        }
        return approvalEngine.submit(club, new ClubApprovalRequest(clubId, ApprovalType.RESIGNATION, userId, userId,
                membership.getClubRole(), ClubPosition.MEMBER, note, Instant.now()));
    }

    public ClubApprovalRequest requestClosure(UUID clubId, UUID userId, String reason) {
        Club club = findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.REQUEST_CLUB_CLOSURE);
        if (requestRepository.existsByClubIdAndTypeAndStatusIn(clubId, ApprovalType.CLUB_CLOSURE, ApprovalStatus.PENDING)) {
            throw new ConflictException("CLOSURE_PENDING", "Kulübün bekleyen bir kapatma talebi var.");
        }
        return approvalEngine.submit(club, new ClubApprovalRequest(clubId, ApprovalType.CLUB_CLOSURE, userId, null,
                null, null, reason, Instant.now()));
    }

    public ClubApprovalRequest requestExpulsion(UUID clubId, UUID userId, UUID studentId, String reason) {
        Club club = findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.PROPOSE_POSITION_CHANGE);
        if (studentId.equals(userId)) {
            throw new BadRequestException("SELF_EXPULSION", "Kendinizi kulüpten çıkaramazsınız.");
        }
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .filter(ClubMembership::isActive)
                .orElseThrow(() -> new NotFoundException("MEMBERSHIP_NOT_FOUND", "Öğrenci kulübün aktif üyesi değil."));
        if (membership.getClubRole() == ClubPosition.PRESIDENT) {
            throw new ConflictException("PRESIDENT_EXPULSION", "Kulüp başkanı bu yolla çıkarılamaz.");
        }
        if (requestRepository.existsByClubIdAndTypeAndSubjectUserIdAndStatusIn(clubId, ApprovalType.MEMBER_EXPULSION,
                studentId, ApprovalStatus.PENDING)) {
            throw new ConflictException("EXPULSION_PENDING", "Bu üye için bekleyen bir çıkarma talebi var.");
        }
        return approvalEngine.submit(club, new ClubApprovalRequest(clubId, ApprovalType.MEMBER_EXPULSION, userId, studentId,
                membership.getClubRole(), null, reason, Instant.now()));
    }

    public ClubApprovalRequest submitDefence(UUID clubId, UUID requestId, UUID userId, String note) {
        ClubApprovalRequest request = approvalEngine.find(clubId, requestId);
        if (request.getType() != ApprovalType.MEMBER_EXPULSION || !userId.equals(request.getSubjectUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Savunmayı yalnızca hakkında çıkarma talebi olan üye ekleyebilir.");
        }
        if (!request.isPending()) {
            throw new ConflictException("REQUEST_DECIDED", "Bu talep zaten işlenmiş.");
        }
        request.respond(note);
        requestRepository.save(request);
        decisionLog.record(request, DecisionAction.DEFENCE_SUBMITTED, userId, note);
        return request;
    }

    @Transactional(readOnly = true)
    public List<ClubDecisionLogEntry> decisionLogOf(UUID clubId, UUID userId) {
        findClub(clubId);
        clubAuthorizationService.require(clubId, userId, ClubPermission.VIEW_DECISIONS);
        return decisionLog.entriesOf(clubId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> requestsOf(UUID clubId, UUID userId) {
        return toResponses(approvalEngine.requestsOf(clubId, userId));
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> inboxOf(UUID userId) {
        return toResponses(approvalEngine.inboxOf(userId));
    }

    public ApprovalRequestResponse approve(UUID clubId, UUID requestId, UUID userId) {
        approvalEngine.find(clubId, requestId);
        return toResponse(approvalEngine.approve(requestId, userId));
    }

    public ApprovalRequestResponse reject(UUID clubId, UUID requestId, UUID userId, String reason) {
        approvalEngine.find(clubId, requestId);
        return toResponse(approvalEngine.reject(requestId, userId, reason));
    }

    public ApprovalRequestResponse withdraw(UUID clubId, UUID requestId, UUID userId) {
        approvalEngine.find(clubId, requestId);
        return toResponse(approvalEngine.withdraw(requestId, userId));
    }

    public ApprovalRequestResponse toResponse(ClubApprovalRequest request) {
        return ApprovalRequestResponse.of(request, clubRepository.findById(request.getClubId()).map(Club::getName).orElse(null),
                detailsLoader.detailsOf(List.of(request)).get(request.getId()), userNames(List.of(request)));
    }

    private List<ApprovalRequestResponse> toResponses(List<ClubApprovalRequest> requests) {
        Map<UUID, String> names = clubRepository.findAllById(requests.stream().map(ClubApprovalRequest::getClubId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Club::getId, Club::getName));
        Map<UUID, ApprovalDetails> details = detailsLoader.detailsOf(requests);
        Map<UUID, String> userNames = userNames(requests);
        return requests.stream()
                .map(request -> ApprovalRequestResponse.of(request, names.get(request.getClubId()), details.get(request.getId()),
                        userNames))
                .toList();
    }

    private Map<UUID, String> userNames(List<ClubApprovalRequest> requests) {
        List<UUID> ids = requests.stream()
                .flatMap(request -> Stream.of(request.getPreparedBy(), request.getSubjectUserId(),
                        request.getPresidentDecidedBy(), request.getDecidedBy()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return userLookup.usersById(ids).values().stream()
                .collect(Collectors.toMap(UserSummary::getId, user -> user.getFirstName() + " " + user.getLastName()));
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı"));
    }
}
