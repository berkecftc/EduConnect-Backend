package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.message.RoleChangeNotificationMessage.Status;
import com.educonnect.clubservice.dto.message.RoleChangeNotificationMessage.Type;
import com.educonnect.clubservice.dto.request.RejectRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.response.RoleChangeRequestDTO;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.RoleChangeRequest;
import com.educonnect.clubservice.model.RoleChangeRequestStatus;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.clubservice.repository.RoleChangeRequestRepository;
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

    private final RoleChangeRequestRepository roleChangeRequestRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;
    private final ClubPositionRules positionRules;
    private final RoleChangeNotifier notifier;
    private final RoleChangeUserNames userNames;
    private final RoleChangeRequestMapper mapper;
    private final ClubLeadershipService leadershipService;

    public RoleChangeDecisionService(RoleChangeRequestRepository roleChangeRequestRepository,
                                     ClubMembershipRepository membershipRepository,
                                     ClubRepository clubRepository,
                                     ClubAuthorizationService clubAuthorizationService,
                                     ClubCacheEvictor cacheEvictor,
                                     ClubManagementStatusPublisher managementStatusPublisher,
                                     ClubPositionRules positionRules,
                                     RoleChangeNotifier notifier,
                                     RoleChangeUserNames userNames,
                                     RoleChangeRequestMapper mapper,
                                     ClubLeadershipService leadershipService) {
        this.roleChangeRequestRepository = roleChangeRequestRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.clubAuthorizationService = clubAuthorizationService;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
        this.positionRules = positionRules;
        this.notifier = notifier;
        this.userNames = userNames;
        this.mapper = mapper;
        this.leadershipService = leadershipService;
    }

    @Transactional(readOnly = true)
    public List<RoleChangeRequestDTO> getPendingRequestsForAdvisor(UUID advisorId) {
        List<Club> advisorClubs = clubRepository.findByAcademicAdvisorId(advisorId);
        if (advisorClubs.isEmpty()) {
            return List.of();
        }

        List<UUID> clubIds = advisorClubs.stream().map(Club::getId).collect(Collectors.toList());
        List<RoleChangeRequest> pendingRequests = roleChangeRequestRepository
                .findByClubIdInAndStatus(clubIds, RoleChangeRequestStatus.PENDING);
        Map<UUID, Club> clubsById = advisorClubs.stream().collect(Collectors.toMap(Club::getId, Function.identity()));

        return mapper.toDtos(pendingRequests, request -> clubsById.get(request.getClubId()));
    }

    public RoleChangeRequestDTO approveRoleChangeRequest(UUID requestId, UUID advisorId) {
        RoleChangeRequest request = findRequest(requestId);
        Club club = authorizePendingDecision(request, advisorId);

        ClubMembership membership = validateRoleChangeStillValid(request);

        ClubPosition previousRole = membership.getClubRole();
        ClubPosition newRole = request.getRequestedRole();
        membership.setClubRole(newRole);
        membership.setActive(true);
        if (newRole == ClubPosition.MEMBER) {
            membership.setTermEndDate(LocalDateTime.now());
        } else {
            membership.setTermStartDate(LocalDateTime.now());
            membership.setTermEndDate(null);
        }
        membershipRepository.save(membership);
        cacheEvictor.evictUser(request.getStudentId());

        managementStatusPublisher.publishCurrentStatus(request.getStudentId());

        request.setStatus(RoleChangeRequestStatus.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(advisorId);
        roleChangeRequestRepository.save(request);

        log.info("Role change request approved: requestId={}, studentId={}, from={}, to={}",
                requestId, request.getStudentId(), previousRole, newRole);

        String studentName = userNames.nameOf(request.getStudentId());
        String studentMessage = newRole == ClubPosition.MEMBER
                ? "Kulüpteki göreviniz sonlandırıldı."
                : "Görev değişikliği talebiniz onaylandı! Yeni göreviniz: " + newRole.displayName();
        notifier.send(request.getStudentId(), club, request.getStudentId(), studentName,
                previousRole, newRole, Status.APPROVED, studentMessage, Type.ROLE_CHANGE_APPROVED);

        UUID presidentId = getClubPresidentId(request.getClubId());
        if (presidentId != null && !presidentId.equals(request.getStudentId())) {
            notifier.send(presidentId, club, request.getStudentId(), studentName,
                    previousRole, newRole,
                    Status.APPROVED, studentName + " için görev değişikliği onaylandı.", Type.ROLE_CHANGE_APPROVED);
        }

        return mapper.toDto(request, club);
    }

    public RoleChangeRequestDTO rejectRoleChangeRequest(UUID requestId, UUID advisorId,
                                                         RejectRoleChangeRequestDTO dto) {
        RoleChangeRequest request = findRequest(requestId);
        Club club = authorizePendingDecision(request, advisorId);

        request.setStatus(RoleChangeRequestStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(advisorId);
        if (dto != null && dto.getRejectionReason() != null) {
            request.setRejectionReason(dto.getRejectionReason());
        }
        roleChangeRequestRepository.save(request);

        log.info("Role change request rejected: requestId={}, studentId={}", requestId, request.getStudentId());

        String studentName = userNames.nameOf(request.getStudentId());
        String rejectionMessage = "Görev değişikliği talebiniz reddedildi.";
        if (dto != null && dto.getRejectionReason() != null) {
            rejectionMessage += " Neden: " + dto.getRejectionReason();
        }
        ClubPosition previousRole = RoleChangeNotifier.previousRoleOf(request);

        notifier.send(request.getStudentId(), club, request.getStudentId(), studentName,
                previousRole, request.getRequestedRole(),
                Status.REJECTED, rejectionMessage, Type.ROLE_CHANGE_REJECTED);

        UUID presidentId = getClubPresidentId(request.getClubId());
        if (presidentId != null) {
            notifier.send(presidentId, club, request.getStudentId(), studentName,
                    previousRole, request.getRequestedRole(),
                    Status.REJECTED, studentName + " için görev değişikliği talebi reddedildi.", Type.ROLE_CHANGE_REJECTED);
        }

        return mapper.toDto(request, club);
    }

    public void removePresidentByAdvisor(UUID clubId, UUID advisorId, String reason) {
        Club club = findClub(clubId);
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
        return roleChangeRequestRepository.countByClubIdAndStatus(clubId, RoleChangeRequestStatus.PENDING);
    }

    private Club findClub(UUID clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));
    }

    private RoleChangeRequest findRequest(UUID requestId) {
        return roleChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talep bulunamadı"));
    }

    private Club authorizePendingDecision(RoleChangeRequest request, UUID advisorId) {
        Club club = findClub(request.getClubId());
        clubAuthorizationService.require(club.getId(), advisorId, ClubPermission.ADVISE);
        if (request.getStatus() != RoleChangeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu talep zaten işlenmiş.");
        }
        return club;
    }

    private ClubMembership validateRoleChangeStillValid(RoleChangeRequest request) {
        ClubMembership membership = membershipRepository
                .findByClubIdAndStudentId(request.getClubId(), request.getStudentId())
                .filter(ClubMembership::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Öğrenci artık kulüp üyesi değil. Talep geçersiz."));

        if (request.getCurrentRole() != null && membership.getClubRole() != request.getCurrentRole()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Öğrencinin görevi talep oluşturulduktan sonra değişmiş. Talep geçersiz.");
        }

        ClubPosition requestedRole = request.getRequestedRole();
        if (requestedRole.isManagement()) {
            positionRules.ensureCapacity(request.getClubId(), requestedRole, false);
            positionRules.ensureNoManagementPositionElsewhere(request.getStudentId(), request.getClubId());
        }
        return membership;
    }

    private UUID getClubPresidentId(UUID clubId) {
        List<ClubMembership> presidents = membershipRepository
                .findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true);
        return presidents.isEmpty() ? null : presidents.get(0).getStudentId();
    }
}
