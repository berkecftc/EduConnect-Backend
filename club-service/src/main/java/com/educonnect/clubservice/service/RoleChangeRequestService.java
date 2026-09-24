package com.educonnect.clubservice.service;

import com.educonnect.clubservice.Repository.ClubMembershipRepository;
import com.educonnect.clubservice.Repository.ClubRepository;
import com.educonnect.clubservice.Repository.RoleChangeRequestRepository;
import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.RoleChangeNotificationMessage;
import com.educonnect.clubservice.dto.request.CreateRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.request.RejectRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.response.RoleChangeRequestDTO;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.*;
import com.educonnect.clubservice.security.ClubAuthorizationService;
import com.educonnect.clubservice.security.ClubPermission;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Kulüp görev değişikliği taleplerini yöneten servis.
 * Tüm görev değişiklikleri akademisyen danışman onayına tabidir.
 */
@Service
@Transactional
public class RoleChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(RoleChangeRequestService.class);

    private static final String ROUTING_KEY_ROLE_CHANGE_NOTIFICATION = "club.role.change.notification";
    private static final String UNKNOWN_USER_NAME = "Bilinmeyen Kullanıcı";

    private final RoleChangeRequestRepository roleChangeRequestRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final UserClient userClient;
    private final RabbitTemplate rabbitTemplate;
    private final ClubAuthorizationService clubAuthorizationService;
    private final ClubCacheEvictor cacheEvictor;
    private final ClubManagementStatusPublisher managementStatusPublisher;

    public RoleChangeRequestService(RoleChangeRequestRepository roleChangeRequestRepository,
                                     ClubMembershipRepository membershipRepository,
                                     ClubRepository clubRepository,
                                     UserClient userClient,
                                     RabbitTemplate rabbitTemplate,
                                     ClubAuthorizationService clubAuthorizationService,
                                     ClubCacheEvictor cacheEvictor,
                                     ClubManagementStatusPublisher managementStatusPublisher) {
        this.roleChangeRequestRepository = roleChangeRequestRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.userClient = userClient;
        this.rabbitTemplate = rabbitTemplate;
        this.clubAuthorizationService = clubAuthorizationService;
        this.cacheEvictor = cacheEvictor;
        this.managementStatusPublisher = managementStatusPublisher;
    }

    // ==================== KULÜP BAŞKANI İŞLEMLERİ ====================

    public RoleChangeRequestDTO createRoleChangeRequest(UUID clubId, CreateRoleChangeRequestDTO dto, UUID requesterId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));
        clubAuthorizationService.require(clubId, requesterId, ClubPermission.PROPOSE_POSITION_CHANGE);

        ClubPosition requestedRole = dto.getRequestedRole();
        if (requestedRole == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Talep edilen görev zorunludur.");
        }
        UUID studentId = resolveStudentId(dto);
        return submitRequest(club, studentId, requestedRole, requesterId);
    }

    public RoleChangeRequestDTO requestRoleRevocation(UUID clubId, UUID studentId, UUID requesterId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));
        clubAuthorizationService.require(clubId, requesterId, ClubPermission.PROPOSE_POSITION_CHANGE);

        if (studentId.equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kendinizi görevden alamazsınız.");
        }
        return submitRequest(club, studentId, ClubPosition.MEMBER, requesterId);
    }

    private RoleChangeRequestDTO submitRequest(Club club, UUID studentId, ClubPosition requestedRole, UUID requesterId) {
        UUID clubId = club.getId();
        ClubMembership studentMembership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .filter(ClubMembership::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Bu öğrenci kulübün aktif üyesi değil. Önce üye olmalı."));

        ClubPosition currentRole = studentMembership.getClubRole();
        if (currentRole == requestedRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Öğrenci zaten bu görevde.");
        }
        if (currentRole == ClubPosition.PRESIDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Başkanın görevi yalnızca danışman kararıyla değiştirilebilir.");
        }

        if (roleChangeRequestRepository.existsByClubIdAndStudentIdAndStatus(
                clubId, studentId, RoleChangeRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu öğrenci için zaten bekleyen bir görev değişikliği talebi var.");
        }

        if (requestedRole.isManagement()) {
            ensureCapacity(clubId, requestedRole, true);
            ensureNoManagementPositionElsewhere(studentId, clubId);
        }

        RoleChangeRequest request = new RoleChangeRequest(clubId, studentId, currentRole, requestedRole, requesterId);
        RoleChangeRequest savedRequest = roleChangeRequestRepository.save(request);

        log.info("Role change request created: clubId={}, studentId={}, from={}, to={}, requesterId={}",
                clubId, studentId, currentRole, requestedRole, requesterId);

        sendNotificationToAdvisor(club, savedRequest,
                requestedRole == ClubPosition.MEMBER
                        ? "Yeni bir görevden alma talebi onayınızı bekliyor."
                        : "Yeni görev değişikliği talebi onayınızı bekliyor.");

        return mapToDTO(savedRequest, club);
    }

    /**
     * Kulüp yetkilisinin oluşturduğu talepleri görüntüler.
     */
    @Transactional(readOnly = true)
    public List<RoleChangeRequestDTO> getClubRoleChangeRequests(UUID clubId, UUID requesterId) {
        clubAuthorizationService.require(clubId, requesterId, ClubPermission.VIEW_MANAGEMENT_DATA);

        Club club = clubRepository.findById(clubId).orElse(null);
        List<RoleChangeRequest> requests = roleChangeRequestRepository.findByClubId(clubId);
        Map<UUID, String> names = fetchUserNames(requests);

        return requests.stream()
                .map(req -> mapToDTO(req, club, names))
                .collect(Collectors.toList());
    }

    // ==================== AKADEMİSYEN (DANIŞMAN) İŞLEMLERİ ====================

    /**
     * Danışmanın sorumlu olduğu kulüplerin bekleyen taleplerini getirir.
     */
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
        Map<UUID, String> names = fetchUserNames(pendingRequests);

        return pendingRequests.stream()
                .map(req -> mapToDTO(req, clubsById.get(req.getClubId()), names))
                .collect(Collectors.toList());
    }

    /**
     * Danışman görev değişikliği talebini onaylar.
     */
    public RoleChangeRequestDTO approveRoleChangeRequest(UUID requestId, UUID advisorId) {
        RoleChangeRequest request = roleChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talep bulunamadı"));

        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));
        clubAuthorizationService.require(club.getId(), advisorId, ClubPermission.ADVISE);

        if (request.getStatus() != RoleChangeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu talep zaten işlenmiş.");
        }

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

        String studentName = fetchUserName(request.getStudentId());
        String studentMessage = newRole == ClubPosition.MEMBER
                ? "Kulüpteki göreviniz sonlandırıldı."
                : "Görev değişikliği talebiniz onaylandı! Yeni göreviniz: " + newRole.displayName();
        sendRoleChangeNotification(request.getStudentId(), club, request.getStudentId(), studentName,
                previousRole.name(), newRole.name(), "APPROVED", studentMessage, "ROLE_CHANGE_APPROVED");

        UUID presidentId = getClubPresidentId(request.getClubId());
        if (presidentId != null && !presidentId.equals(request.getStudentId())) {
            sendRoleChangeNotification(presidentId, club, request.getStudentId(), studentName,
                    previousRole.name(), newRole.name(),
                    "APPROVED", studentName + " için görev değişikliği onaylandı.", "ROLE_CHANGE_APPROVED");
        }

        return mapToDTO(request, club);
    }

    /**
     * Danışman görev değişikliği talebini reddeder.
     */
    public RoleChangeRequestDTO rejectRoleChangeRequest(UUID requestId, UUID advisorId,
                                                         RejectRoleChangeRequestDTO dto) {
        RoleChangeRequest request = roleChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talep bulunamadı"));

        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));
        clubAuthorizationService.require(club.getId(), advisorId, ClubPermission.ADVISE);

        if (request.getStatus() != RoleChangeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu talep zaten işlenmiş.");
        }

        request.setStatus(RoleChangeRequestStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(advisorId);
        if (dto != null && dto.getRejectionReason() != null) {
            request.setRejectionReason(dto.getRejectionReason());
        }
        roleChangeRequestRepository.save(request);

        log.info("Role change request rejected: requestId={}, studentId={}", requestId, request.getStudentId());

        String studentName = fetchUserName(request.getStudentId());
        String rejectionMessage = "Görev değişikliği talebiniz reddedildi.";
        if (dto != null && dto.getRejectionReason() != null) {
            rejectionMessage += " Neden: " + dto.getRejectionReason();
        }
        String previousRoleName = request.getCurrentRole() != null
                ? request.getCurrentRole().name() : ClubPosition.MEMBER.name();

        sendRoleChangeNotification(request.getStudentId(), club, request.getStudentId(), studentName,
                previousRoleName, request.getRequestedRole().name(),
                "REJECTED", rejectionMessage, "ROLE_CHANGE_REJECTED");

        UUID presidentId = getClubPresidentId(request.getClubId());
        if (presidentId != null) {
            sendRoleChangeNotification(presidentId, club, request.getStudentId(), studentName,
                    previousRoleName, request.getRequestedRole().name(),
                    "REJECTED", studentName + " için görev değişikliği talebi reddedildi.", "ROLE_CHANGE_REJECTED");
        }

        return mapToDTO(request, club);
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

        log.info("President removed by advisor: clubId={}, studentId={}, advisorId={}",
                clubId, president.getStudentId(), advisorId);

        String studentName = fetchUserName(president.getStudentId());
        String message = "Kulüp başkanlığı göreviniz danışman kararıyla sonlandırıldı.";
        if (reason != null && !reason.isBlank()) {
            message += " Neden: " + reason;
        }
        sendRoleChangeNotification(president.getStudentId(), club, president.getStudentId(), studentName,
                ClubPosition.PRESIDENT.name(), ClubPosition.MEMBER.name(), "APPROVED", message, "ROLE_REVOKED");
    }

    /**
     * Belirli bir kulübün bekleyen talep sayısını döndürür (danışman için).
     */
    @Transactional(readOnly = true)
    public long getPendingRequestCountForClub(UUID clubId, UUID advisorId) {
        clubAuthorizationService.require(clubId, advisorId, ClubPermission.ADVISE);
        return roleChangeRequestRepository.countByClubIdAndStatus(clubId, RoleChangeRequestStatus.PENDING);
    }

    // ==================== YARDIMCI METOTLAR ====================

    private void ensureCapacity(UUID clubId, ClubPosition position, boolean includePending) {
        long holders = membershipRepository.findByClubIdAndClubRoleAndIsActive(clubId, position, true).size();
        long pending = includePending
                ? roleChangeRequestRepository.countByClubIdAndRequestedRoleAndStatus(
                        clubId, position, RoleChangeRequestStatus.PENDING)
                : 0;
        if (holders + pending >= position.maxActiveHolders()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, position.maxActiveHolders() == 1
                    ? "Bu görev dolu veya bu görev için bekleyen bir talep var."
                    : "Bu görev için en fazla " + position.maxActiveHolders() + " kişi olabilir.");
        }
    }

    private void ensureNoManagementPositionElsewhere(UUID studentId, UUID clubId) {
        clubAuthorizationService.activeManagementPositionOf(studentId)
                .filter(membership -> !membership.getClubId().equals(clubId))
                .ifPresent(membership -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Bu öğrencinin başka bir kulüpte yönetim görevi var. "
                                    + "Bir öğrenci yalnızca bir kulüpte yönetim görevi alabilir.");
                });
    }

    /**
     * Onay anında görev değişikliğinin hâlâ geçerli olup olmadığını kontrol eder.
     */
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
            ensureCapacity(request.getClubId(), requestedRole, false);
            ensureNoManagementPositionElsewhere(request.getStudentId(), request.getClubId());
        }
        return membership;
    }

    private UUID getClubPresidentId(UUID clubId) {
        List<ClubMembership> presidents = membershipRepository
                .findByClubIdAndClubRoleAndIsActive(clubId, ClubPosition.PRESIDENT, true);

        if (!presidents.isEmpty()) {
            return presidents.get(0).getStudentId();
        }
        return null;
    }

    private String fetchUserName(UUID userId) {
        try {
            UserSummary user = userClient.getUserById(userId);
            if (user != null) {
                return user.getFirstName() + " " + user.getLastName();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user name for userId={}: {}", userId, e.getMessage());
        }
        return UNKNOWN_USER_NAME;
    }

    private Map<UUID, String> fetchUserNames(List<RoleChangeRequest> requests) {
        List<UUID> userIds = requests.stream()
                .flatMap(request -> Stream.of(request.getStudentId(), request.getRequesterId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return userClient.getUsersByIds(userIds).stream()
                    .filter(user -> user != null && user.getId() != null)
                    .collect(Collectors.toMap(UserSummary::getId,
                            user -> user.getFirstName() + " " + user.getLastName(),
                            (first, second) -> first));
        } catch (Exception e) {
            log.warn("Failed to fetch {} user names: {}", userIds.size(), e.getMessage());
            return Map.of();
        }
    }

    private void sendNotificationToAdvisor(Club club, RoleChangeRequest request, String message) {
        String studentName = fetchUserName(request.getStudentId());

        sendRoleChangeNotification(
                club.getAcademicAdvisorId(),
                club,
                request.getStudentId(),
                studentName,
                request.getCurrentRole() != null ? request.getCurrentRole().name() : ClubPosition.MEMBER.name(),
                request.getRequestedRole().name(),
                "PENDING",
                message,
                "ROLE_CHANGE_REQUEST"
        );
    }

    private void sendRoleChangeNotification(UUID targetUserId, Club club, UUID affectedStudentId,
                                             String affectedStudentName, String previousRole,
                                             String newRole, String status, String message,
                                             String notificationType) {
        if (targetUserId == null) {
            return;
        }
        try {
            RoleChangeNotificationMessage notificationMessage = new RoleChangeNotificationMessage(
                    targetUserId,
                    club.getId(),
                    club.getName(),
                    affectedStudentId,
                    affectedStudentName,
                    previousRole,
                    newRole,
                    status,
                    message,
                    notificationType
            );

            rabbitTemplate.convertAndSend(
                    ClubRabbitMQConfig.CLUB_EXCHANGE_NAME,
                    ROUTING_KEY_ROLE_CHANGE_NOTIFICATION,
                    notificationMessage
            );
        } catch (Exception e) {
            log.error("Failed to send role change notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Hybrid DTO'dan öğrenci UUID'sini çözümler.
     * Öncelik sırası: studentId > studentNumber
     */
    private UUID resolveStudentId(CreateRoleChangeRequestDTO dto) {
        if (!dto.hasStudentIdentifier()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Öğrenci ID veya öğrenci numarası zorunludur.");
        }

        if (dto.hasStudentId()) {
            try {
                return UUID.fromString(dto.getStudentId().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Geçersiz öğrenci ID formatı: " + dto.getStudentId());
            }
        }

        return resolveStudentIdByStudentNumber(dto.getStudentNumber().trim());
    }

    /**
     * Öğrenci numarasından UUID'yi çözümler (user-service'e Feign çağrısı).
     */
    private UUID resolveStudentIdByStudentNumber(String studentNumber) {
        if (studentNumber == null || studentNumber.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Geçersiz öğrenci numarası formatı.");
        }

        try {
            UserSummary userSummary = userClient.getUserByStudentNumber(studentNumber);

            if (userSummary == null || userSummary.getId() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bu öğrenci numarasına sahip kullanıcı bulunamadı: " + studentNumber);
            }
            return userSummary.getId();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bu öğrenci numarasına sahip kullanıcı bulunamadı: " + studentNumber);
            }
            log.error("Feign error while resolving student number, status: {}", e.status());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kullanıcı servisi şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyin.");
        } catch (Exception e) {
            log.error("Unexpected error while resolving student number", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Öğrenci bilgisi alınırken beklenmeyen bir hata oluştu.");
        }
    }

    private RoleChangeRequestDTO mapToDTO(RoleChangeRequest request, Club club) {
        Map<UUID, String> names = new HashMap<>();
        names.put(request.getStudentId(), fetchUserName(request.getStudentId()));
        names.put(request.getRequesterId(), fetchUserName(request.getRequesterId()));
        return mapToDTO(request, club, names);
    }

    private RoleChangeRequestDTO mapToDTO(RoleChangeRequest request, Club club, Map<UUID, String> names) {
        RoleChangeRequestDTO dto = new RoleChangeRequestDTO();
        dto.setId(request.getId());
        dto.setClubId(request.getClubId());
        dto.setClubName(club != null ? club.getName() : null);
        dto.setStudentId(request.getStudentId());
        dto.setStudentName(names.getOrDefault(request.getStudentId(), UNKNOWN_USER_NAME));
        dto.setCurrentRole(request.getCurrentRole());
        dto.setRequestedRole(request.getRequestedRole());
        dto.setRequesterId(request.getRequesterId());
        dto.setRequesterName(names.getOrDefault(request.getRequesterId(), UNKNOWN_USER_NAME));
        dto.setStatus(request.getStatus());
        dto.setRejectionReason(request.getRejectionReason());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setProcessedAt(request.getProcessedAt());
        return dto;
    }
}
