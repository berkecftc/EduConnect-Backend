package com.educonnect.clubservice.service;

import com.educonnect.clubservice.Repository.ClubMembershipRepository;
import com.educonnect.clubservice.Repository.ClubRepository;
import com.educonnect.clubservice.Repository.RoleChangeRequestRepository;
import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.AssignClubRoleMessage;
import com.educonnect.clubservice.dto.message.RevokeClubRoleMessage;
import com.educonnect.clubservice.dto.message.RoleChangeNotificationMessage;
import com.educonnect.clubservice.dto.request.CreateRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.request.RejectRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.response.RoleChangeRequestDTO;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.*;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Kulüp görev değişikliği taleplerini yöneten servis.
 * Tüm görev değişiklikleri akademisyen danışman onayına tabidir.
 */
@Service
@Transactional
public class RoleChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(RoleChangeRequestService.class);

    private static final String ROUTING_KEY_ROLE_CHANGE_NOTIFICATION = "club.role.change.notification";

    private final RoleChangeRequestRepository roleChangeRequestRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final UserClient userClient;
    private final RabbitTemplate rabbitTemplate;

    public RoleChangeRequestService(RoleChangeRequestRepository roleChangeRequestRepository,
                                     ClubMembershipRepository membershipRepository,
                                     ClubRepository clubRepository,
                                     UserClient userClient,
                                     RabbitTemplate rabbitTemplate) {
        this.roleChangeRequestRepository = roleChangeRequestRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.userClient = userClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ==================== KULÜP YETKİLİSİ İŞLEMLERİ ====================

    /**
     * Görev değişikliği talebi oluşturur.
     * Hybrid DTO desteği: studentId VEYA studentNumber kullanılabilir.
     * Validasyonlar:
     * 1. En az bir öğrenci tanımlayıcısı (studentId veya studentNumber) zorunlu
     * 2. Talep edilen pozisyon şu anda boş olmalı
     * 3. Başkanlık talebi ise, öğrenci başka bir kulüpte başkan olmamalı
     * 4. Aynı pozisyon için bekleyen başka talep olmamalı
     */
    public RoleChangeRequestDTO createRoleChangeRequest(UUID clubId, CreateRoleChangeRequestDTO dto, UUID requesterId) {
        // 1. Kulüp var mı kontrol et
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));

        // 2. Öğrenci ID'sini çözümle (hybrid DTO desteği)
        UUID studentId = resolveStudentId(dto);
        ClubRole requestedRole = dto.getRequestedRole();

        // 2. Talebi oluşturan kişi yetkili mi kontrol et
        verifyClubOfficial(clubId, requesterId);

        // 3. Hedef öğrenci kulübe üye mi kontrol et
        ClubMembership studentMembership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Bu öğrenci kulübe üye değil. Önce üye olarak eklenmeli."));

        // 4. Öğrenci zaten bu rolde mi kontrol et
        if (studentMembership.getClubRole() == requestedRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Öğrenci zaten bu rolde.");
        }

        // 5. Talep edilen pozisyon şu anda dolu mu kontrol et (ROLE_MEMBER hariç)
        if (requestedRole != ClubRole.ROLE_MEMBER) {
            List<ClubMembership> existingRoleHolders = membershipRepository
                    .findByClubIdAndClubRoleAndIsActive(clubId, requestedRole, true);

            if (!existingRoleHolders.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bu pozisyon şu anda dolu. Önce mevcut kişiyi görevden almalısınız.");
            }
        }

        // 6. Başkanlık talebi ise, öğrenci başka bir kulüpte başkan mı kontrol et
        if (requestedRole == ClubRole.ROLE_CLUB_OFFICIAL) {
            boolean isPresidentElsewhere = membershipRepository
                    .existsByStudentIdAndClubRoleAndIsActive(studentId, ClubRole.ROLE_CLUB_OFFICIAL, true);

            if (isPresidentElsewhere) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bu öğrenci başka bir kulüpte zaten başkan. Bir kişi sadece bir kulübün başkanı olabilir.");
            }
        }

        // 7. Aynı pozisyon için bekleyen başka talep var mı kontrol et
        if (roleChangeRequestRepository.existsByClubIdAndRequestedRoleAndStatus(
                clubId, requestedRole, RoleChangeRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu pozisyon için zaten bekleyen bir talep var.");
        }

        // 8. Aynı öğrenci için bekleyen talep var mı kontrol et
        if (roleChangeRequestRepository.existsByClubIdAndStudentIdAndStatus(
                clubId, studentId, RoleChangeRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu öğrenci için zaten bekleyen bir görev değişikliği talebi var.");
        }

        // 9. Yeni talep oluştur
        RoleChangeRequest request = new RoleChangeRequest(
                clubId,
                studentId,
                studentMembership.getClubRole(),
                requestedRole,
                requesterId
        );
        RoleChangeRequest savedRequest = roleChangeRequestRepository.save(request);

        log.info("Role change request created: clubId={}, studentId={}, requestedRole={}, requesterId={}",
                clubId, studentId, requestedRole, requesterId);

        // 10. Danışmana bildirim gönder
        sendNotificationToAdvisor(club, savedRequest, "ROLE_CHANGE_REQUEST",
                "Yeni görev değişikliği talebi onayınızı bekliyor.");

        return mapToDTO(savedRequest, club);
    }

    /**
     * Bir üyeyi görevden alır (rolünü ROLE_MEMBER yapar).
     * Bu işlem doğrudan yapılır, onay gerektirmez.
     */
    public void revokeRole(UUID clubId, UUID studentId, UUID requesterId) {
        // 1. Kulüp var mı kontrol et
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));

        // 2. Talebi oluşturan kişi yetkili mi kontrol et
        verifyClubOfficial(clubId, requesterId);

        // 3. Hedef üyeliği bul
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Üyelik bulunamadı"));

        // 4. Zaten normal üye mi kontrol et
        if (membership.getClubRole() == ClubRole.ROLE_MEMBER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bu üye zaten normal üye rolünde.");
        }

        // 5. Kendini görevden alamaz (başkan kendini görevden alamaz)
        if (studentId.equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kendinizi görevden alamazsınız.");
        }

        ClubRole previousRole = membership.getClubRole();

        // 6. Eğer başkan görevden alınıyorsa, auth-service'e mesaj gönder
        if (previousRole == ClubRole.ROLE_CLUB_OFFICIAL) {
            try {
                RevokeClubRoleMessage revokeMessage = new RevokeClubRoleMessage(
                        studentId,
                        "ROLE_CLUB_OFFICIAL",
                        clubId
                );
                rabbitTemplate.convertAndSend(
                        ClubRabbitMQConfig.EXCHANGE_NAME,
                        "user.role.revoke",
                        revokeMessage
                );
                log.info("Sent role revoke message for user {} from club {}", studentId, clubId);
            } catch (Exception e) {
                log.error("Failed to send role revoke message: {}", e.getMessage(), e);
            }
        }

        // 7. Rolü güncelle
        membership.setClubRole(ClubRole.ROLE_MEMBER);
        membership.setTermEndDate(LocalDateTime.now());
        membershipRepository.save(membership);

        log.info("Role revoked: clubId={}, studentId={}, previousRole={}", clubId, studentId, previousRole);

        // 8. Bildirimleri gönder (öğrenci, başkan, danışman)
        String studentName = fetchUserName(studentId);

        // Öğrenciye bildirim
        sendRoleChangeNotification(studentId, club, studentId, studentName,
                previousRole.toString(), ClubRole.ROLE_MEMBER.toString(),
                "ROLE_REVOKED", "Kulüpteki göreviniz sonlandırıldı.", "ROLE_REVOKED");

        // Başkana bildirim (eğer başkan değilse)
        UUID presidentId = getClubPresidentId(clubId);
        if (presidentId != null && !presidentId.equals(requesterId)) {
            sendRoleChangeNotification(presidentId, club, studentId, studentName,
                    previousRole.toString(), ClubRole.ROLE_MEMBER.toString(),
                    "ROLE_REVOKED", studentName + " görevden alındı.", "ROLE_REVOKED");
        }

        // Danışmana bildirim
        sendRoleChangeNotification(club.getAcademicAdvisorId(), club, studentId, studentName,
                previousRole.toString(), ClubRole.ROLE_MEMBER.toString(),
                "ROLE_REVOKED", studentName + " görevden alındı.", "ROLE_REVOKED");
    }

    /**
     * Kulüp yetkilisinin oluşturduğu talepleri görüntüler.
     */
    @Transactional(readOnly = true)
    public List<RoleChangeRequestDTO> getClubRoleChangeRequests(UUID clubId, UUID requesterId) {
        verifyClubOfficial(clubId, requesterId);

        Club club = clubRepository.findById(clubId).orElse(null);
        List<RoleChangeRequest> requests = roleChangeRequestRepository.findByClubId(clubId);

        return requests.stream()
                .map(req -> mapToDTO(req, club))
                .collect(Collectors.toList());
    }

    // ==================== AKADEMİSYEN (DANIŞMAN) İŞLEMLERİ ====================

    /**
     * Danışmanın sorumlu olduğu kulüplerin bekleyen taleplerini getirir.
     */
    @Transactional(readOnly = true)
    public List<RoleChangeRequestDTO> getPendingRequestsForAdvisor(UUID advisorId) {
        // Danışmanın sorumlu olduğu kulüpleri bul
        List<Club> advisorClubs = clubRepository.findByAcademicAdvisorId(advisorId);

        if (advisorClubs.isEmpty()) {
            return List.of();
        }

        List<UUID> clubIds = advisorClubs.stream().map(Club::getId).collect(Collectors.toList());

        // Bu kulüplerin bekleyen taleplerini getir
        List<RoleChangeRequest> pendingRequests = roleChangeRequestRepository
                .findByClubIdInAndStatus(clubIds, RoleChangeRequestStatus.PENDING);

        return pendingRequests.stream()
                .map(req -> {
                    Club club = advisorClubs.stream()
                            .filter(c -> c.getId().equals(req.getClubId()))
                            .findFirst()
                            .orElse(null);
                    return mapToDTO(req, club);
                })
                .collect(Collectors.toList());
    }

    /**
     * Danışman görev değişikliği talebini onaylar.
     */
    public RoleChangeRequestDTO approveRoleChangeRequest(UUID requestId, UUID advisorId) {
        // 1. Talebi bul
        RoleChangeRequest request = roleChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talep bulunamadı"));

        // 2. Kulübü bul ve danışman yetkisini kontrol et
        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));

        if (!club.getAcademicAdvisorId().equals(advisorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu kulübün danışmanı değilsiniz.");
        }

        // 3. Talep beklemede mi kontrol et
        if (request.getStatus() != RoleChangeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bu talep zaten işlenmiş.");
        }

        // 4. Tekrar validasyon yap (pozisyon hala boş mu, öğrenci hala uygun mu)
        validateRoleChangeStillValid(request);

        // 5. Üyeliği güncelle
        ClubMembership membership = membershipRepository
                .findByClubIdAndStudentId(request.getClubId(), request.getStudentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Üyelik bulunamadı. Öğrenci kulüpten ayrılmış olabilir."));

        ClubRole previousRole = membership.getClubRole();
        membership.setClubRole(request.getRequestedRole());
        membership.setTermStartDate(LocalDateTime.now());
        membership.setActive(true);
        membershipRepository.save(membership);

        // 6. Eğer başkanlık atandıysa, auth-service'e mesaj gönder
        if (request.getRequestedRole() == ClubRole.ROLE_CLUB_OFFICIAL) {
            try {
                AssignClubRoleMessage assignMessage = new AssignClubRoleMessage(
                        request.getStudentId(),
                        "ROLE_CLUB_OFFICIAL",
                        request.getClubId()
                );
                rabbitTemplate.convertAndSend(
                        ClubRabbitMQConfig.EXCHANGE_NAME,
                        "user.role.assign",
                        assignMessage
                );
                log.info("Sent role assignment message for user {} to become ROLE_CLUB_OFFICIAL of club {}",
                        request.getStudentId(), request.getClubId());
            } catch (Exception e) {
                log.error("Failed to send role assignment message: {}", e.getMessage(), e);
            }
        }

        // 7. Talebi güncelle
        request.setStatus(RoleChangeRequestStatus.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(advisorId);
        roleChangeRequestRepository.save(request);

        log.info("Role change request approved: requestId={}, studentId={}, newRole={}",
                requestId, request.getStudentId(), request.getRequestedRole());

        // 8. Bildirimleri gönder
        String studentName = fetchUserName(request.getStudentId());

        // Öğrenciye bildirim
        sendRoleChangeNotification(request.getStudentId(), club, request.getStudentId(), studentName,
                previousRole.toString(), request.getRequestedRole().toString(),
                "APPROVED", "Görev değişikliği talebiniz onaylandı! Yeni göreviniz: " +
                        getRoleTurkishName(request.getRequestedRole()), "ROLE_CHANGE_APPROVED");

        // Başkana bildirim
        UUID presidentId = getClubPresidentId(request.getClubId());
        if (presidentId != null && !presidentId.equals(request.getStudentId())) {
            sendRoleChangeNotification(presidentId, club, request.getStudentId(), studentName,
                    previousRole.toString(), request.getRequestedRole().toString(),
                    "APPROVED", studentName + " için görev değişikliği onaylandı.", "ROLE_CHANGE_APPROVED");
        }

        // Danışmana bildirim (kendi onayladığı için opsiyonel, ama log amaçlı)
        log.info("Role change approved by advisor: advisorId={}, studentId={}, newRole={}",
                advisorId, request.getStudentId(), request.getRequestedRole());

        return mapToDTO(request, club);
    }

    /**
     * Danışman görev değişikliği talebini reddeder.
     */
    public RoleChangeRequestDTO rejectRoleChangeRequest(UUID requestId, UUID advisorId,
                                                         RejectRoleChangeRequestDTO dto) {
        // 1. Talebi bul
        RoleChangeRequest request = roleChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talep bulunamadı"));

        // 2. Kulübü bul ve danışman yetkisini kontrol et
        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));

        if (!club.getAcademicAdvisorId().equals(advisorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu kulübün danışmanı değilsiniz.");
        }

        // 3. Talep beklemede mi kontrol et
        if (request.getStatus() != RoleChangeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bu talep zaten işlenmiş.");
        }

        // 4. Talebi reddet
        request.setStatus(RoleChangeRequestStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(advisorId);
        if (dto != null && dto.getRejectionReason() != null) {
            request.setRejectionReason(dto.getRejectionReason());
        }
        roleChangeRequestRepository.save(request);

        log.info("Role change request rejected: requestId={}, studentId={}, reason={}",
                requestId, request.getStudentId(), request.getRejectionReason());

        // 5. Bildirimleri gönder
        String studentName = fetchUserName(request.getStudentId());
        String rejectionMessage = "Görev değişikliği talebiniz reddedildi.";
        if (dto != null && dto.getRejectionReason() != null) {
            rejectionMessage += " Neden: " + dto.getRejectionReason();
        }

        // Öğrenciye bildirim
        sendRoleChangeNotification(request.getStudentId(), club, request.getStudentId(), studentName,
                request.getCurrentRole() != null ? request.getCurrentRole().toString() : "ROLE_MEMBER",
                request.getRequestedRole().toString(),
                "REJECTED", rejectionMessage, "ROLE_CHANGE_REJECTED");

        // Başkana bildirim
        UUID presidentId = getClubPresidentId(request.getClubId());
        if (presidentId != null) {
            sendRoleChangeNotification(presidentId, club, request.getStudentId(), studentName,
                    request.getCurrentRole() != null ? request.getCurrentRole().toString() : "ROLE_MEMBER",
                    request.getRequestedRole().toString(),
                    "REJECTED", studentName + " için görev değişikliği talebi reddedildi.", "ROLE_CHANGE_REJECTED");
        }

        return mapToDTO(request, club);
    }

    /**
     * Belirli bir kulübün bekleyen talep sayısını döndürür (danışman için).
     */
    @Transactional(readOnly = true)
    public long getPendingRequestCountForClub(UUID clubId, UUID advisorId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));

        if (!club.getAcademicAdvisorId().equals(advisorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu kulübün danışmanı değilsiniz.");
        }

        return roleChangeRequestRepository.countByClubIdAndStatus(clubId, RoleChangeRequestStatus.PENDING);
    }

    // ==================== YARDIMCI METOTLAR ====================

    /**
     * Kullanıcının belirtilen kulübün yetkilisi olup olmadığını kontrol eder.
     */
    private void verifyClubOfficial(UUID clubId, UUID userId) {
        ClubMembership membership = membershipRepository.findByClubIdAndStudentId(clubId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Bu kulübün üyesi değilsiniz."));

        // Sadece başkan, başkan yardımcısı veya YK üyesi görev değişikliği talebi oluşturabilir
        ClubRole role = membership.getClubRole();
        if (role != ClubRole.ROLE_CLUB_OFFICIAL &&
            role != ClubRole.ROLE_VICE_PRESIDENT &&
            role != ClubRole.ROLE_BOARD_MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Görev değişikliği talebi oluşturma yetkiniz yok.");
        }
    }

    /**
     * Onay anında görev değişikliğinin hala geçerli olup olmadığını kontrol eder.
     */
    private void validateRoleChangeStillValid(RoleChangeRequest request) {
        // Pozisyon hala boş mu?
        if (request.getRequestedRole() != ClubRole.ROLE_MEMBER) {
            List<ClubMembership> existingRoleHolders = membershipRepository
                    .findByClubIdAndClubRoleAndIsActive(request.getClubId(), request.getRequestedRole(), true);

            if (!existingRoleHolders.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bu pozisyon artık dolu. Talep geçersiz.");
            }
        }

        // Başkanlık talebi ise öğrenci başka yerde başkan mı?
        if (request.getRequestedRole() == ClubRole.ROLE_CLUB_OFFICIAL) {
            boolean isPresidentElsewhere = membershipRepository
                    .existsByStudentIdAndClubRoleAndIsActive(request.getStudentId(), ClubRole.ROLE_CLUB_OFFICIAL, true);

            if (isPresidentElsewhere) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Öğrenci artık başka bir kulüpte başkan. Talep geçersiz.");
            }
        }

        // Öğrenci hala kulüp üyesi mi?
        if (!membershipRepository.existsByClubIdAndStudentId(request.getClubId(), request.getStudentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Öğrenci artık kulüp üyesi değil. Talep geçersiz.");
        }
    }

    /**
     * Kulübün başkan ID'sini döndürür.
     */
    private UUID getClubPresidentId(UUID clubId) {
        List<ClubMembership> presidents = membershipRepository
                .findByClubIdAndClubRoleAndIsActive(clubId, ClubRole.ROLE_CLUB_OFFICIAL, true);

        if (!presidents.isEmpty()) {
            return presidents.get(0).getStudentId();
        }
        return null;
    }

    /**
     * User-service'den kullanıcı adını çeker.
     */
    private String fetchUserName(UUID userId) {
        try {
            UserSummary user = userClient.getUserById(userId);
            if (user != null) {
                return user.getFirstName() + " " + user.getLastName();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user name for userId={}: {}", userId, e.getMessage());
        }
        return "Bilinmeyen Kullanıcı";
    }

    /**
     * Danışmana bildirim gönderir.
     */
    private void sendNotificationToAdvisor(Club club, RoleChangeRequest request,
                                            String notificationType, String message) {
        String studentName = fetchUserName(request.getStudentId());

        sendRoleChangeNotification(
                club.getAcademicAdvisorId(),
                club,
                request.getStudentId(),
                studentName,
                request.getCurrentRole() != null ? request.getCurrentRole().toString() : "ROLE_MEMBER",
                request.getRequestedRole().toString(),
                "PENDING",
                message,
                notificationType
        );
    }

    /**
     * RabbitMQ ile görev değişikliği bildirimi gönderir.
     */
    private void sendRoleChangeNotification(UUID targetUserId, Club club, UUID affectedStudentId,
                                             String affectedStudentName, String previousRole,
                                             String newRole, String status, String message,
                                             String notificationType) {
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
                    ClubRabbitMQConfig.EXCHANGE_NAME,
                    ROUTING_KEY_ROLE_CHANGE_NOTIFICATION,
                    notificationMessage
            );

            log.info("Role change notification sent: targetUserId={}, type={}, status={}",
                    targetUserId, notificationType, status);
        } catch (Exception e) {
            log.error("Failed to send role change notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Rol enum'unu Türkçe isme çevirir.
     */
    private String getRoleTurkishName(ClubRole role) {
        return switch (role) {
            case ROLE_CLUB_OFFICIAL -> "Kulüp Başkanı";
            case ROLE_VICE_PRESIDENT -> "Başkan Yardımcısı";
            case ROLE_SECRETARY -> "Sekreter";
            case ROLE_TREASURER -> "Sayman";
            case ROLE_BOARD_MEMBER -> "Yönetim Kurulu Üyesi";
            case ROLE_MEMBER -> "Üye";
        };
    }

    /**
     * Hybrid DTO'dan öğrenci UUID'sini çözümler.
     * Öncelik sırası: studentId > studentNumber
     *
     * @param dto CreateRoleChangeRequestDTO
     * @return Çözümlenen öğrenci UUID'si
     * @throws ResponseStatusException Öğrenci tanımlayıcısı yoksa veya geçersizse
     */
    private UUID resolveStudentId(CreateRoleChangeRequestDTO dto) {
        // 1. En az bir tanımlayıcı zorunlu
        if (!dto.hasStudentIdentifier()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Öğrenci ID veya öğrenci numarası zorunludur.");
        }

        // 2. studentId varsa öncelikli olarak kullan
        if (dto.hasStudentId()) {
            try {
                return UUID.fromString(dto.getStudentId().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Geçersiz öğrenci ID formatı: " + dto.getStudentId());
            }
        }

        // 3. studentNumber varsa user-service'den UUID'yi çözümle
        if (dto.hasStudentNumber()) {
            return resolveStudentIdByStudentNumber(dto.getStudentNumber().trim());
        }

        // Bu noktaya ulaşılmamalı, güvenlik için exception fırlat
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Öğrenci ID veya öğrenci numarası zorunludur.");
    }

    /**
     * Öğrenci numarasından UUID'yi çözümler.
     * User-service'e Feign çağrısı yapar.
     *
     * @param studentNumber Öğrenci numarası
     * @return Öğrenci UUID'si
     * @throws ResponseStatusException Öğrenci bulunamazsa veya servis hatası varsa
     */
    private UUID resolveStudentIdByStudentNumber(String studentNumber) {
        // Öğrenci numarası format kontrolü (opsiyonel - gerekirse açılabilir)
        if (studentNumber == null || studentNumber.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Geçersiz öğrenci numarası formatı.");
        }

        try {
            log.info("Resolving student ID by student number: {}", studentNumber);
            UserSummary userSummary = userClient.getUserByStudentNumber(studentNumber);

            if (userSummary == null || userSummary.getId() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bu öğrenci numarasına sahip kullanıcı bulunamadı: " + studentNumber);
            }

            log.info("Student ID resolved: studentNumber={}, studentId={}",
                    studentNumber, userSummary.getId());
            return userSummary.getId();

        } catch (FeignException.NotFound e) {
            log.warn("Student not found by student number: {}", studentNumber);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Bu öğrenci numarasına sahip kullanıcı bulunamadı: " + studentNumber);
        } catch (FeignException.ServiceUnavailable e) {
            log.error("User service unavailable while resolving student number: {}", studentNumber, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kullanıcı servisi şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyin.");
        } catch (FeignException e) {
            log.error("Feign error while resolving student number: {}, status: {}",
                    studentNumber, e.status(), e);
            if (e.status() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bu öğrenci numarasına sahip kullanıcı bulunamadı: " + studentNumber);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kullanıcı servisi şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyin.");
        } catch (Exception e) {
            log.error("Unexpected error while resolving student number: {}", studentNumber, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Öğrenci bilgisi alınırken beklenmeyen bir hata oluştu.");
        }
    }

    /**
     * Entity'yi DTO'ya dönüştürür.
     */
    private RoleChangeRequestDTO mapToDTO(RoleChangeRequest request, Club club) {
        RoleChangeRequestDTO dto = new RoleChangeRequestDTO();
        dto.setId(request.getId());
        dto.setClubId(request.getClubId());
        dto.setClubName(club != null ? club.getName() : null);
        dto.setStudentId(request.getStudentId());
        dto.setStudentName(fetchUserName(request.getStudentId()));
        dto.setCurrentRole(request.getCurrentRole());
        dto.setRequestedRole(request.getRequestedRole());
        dto.setRequesterId(request.getRequesterId());
        dto.setRequesterName(fetchUserName(request.getRequesterId()));
        dto.setStatus(request.getStatus());
        dto.setRejectionReason(request.getRejectionReason());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setProcessedAt(request.getProcessedAt());
        return dto;
    }
}

