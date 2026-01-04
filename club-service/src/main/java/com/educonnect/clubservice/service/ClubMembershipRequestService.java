package com.educonnect.clubservice.service;

import com.educonnect.clubservice.Repository.ClubMembershipRepository;
import com.educonnect.clubservice.Repository.ClubMembershipRequestRepository;
import com.educonnect.clubservice.Repository.ClubRepository;
import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.MembershipRequestMessage;
import com.educonnect.clubservice.dto.request.CreateMembershipRequestDTO;
import com.educonnect.clubservice.dto.request.RejectMembershipRequestDTO;
import com.educonnect.clubservice.dto.response.MembershipRequestDTO;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.*;
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

@Service
@Transactional
public class ClubMembershipRequestService {

    private static final Logger log = LoggerFactory.getLogger(ClubMembershipRequestService.class);

    private static final String ROUTING_KEY_MEMBERSHIP_NOTIFICATION = "club.membership.notification";

    private final ClubMembershipRequestRepository requestRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ClubRepository clubRepository;
    private final UserClient userClient;
    private final RabbitTemplate rabbitTemplate;

    public ClubMembershipRequestService(ClubMembershipRequestRepository requestRepository,
                                         ClubMembershipRepository membershipRepository,
                                         ClubRepository clubRepository,
                                         UserClient userClient,
                                         RabbitTemplate rabbitTemplate) {
        this.requestRepository = requestRepository;
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
        this.userClient = userClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Öğrenci bir kulübe üyelik isteği gönderir.
     */
    public MembershipRequestDTO createMembershipRequest(UUID clubId, UUID studentId, CreateMembershipRequestDTO dto) {
        // 1. Kulüp var mı kontrol et
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kulüp bulunamadı"));

        // 2. Zaten üye mi kontrol et
        if (membershipRepository.findByClubIdAndStudentId(clubId, studentId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu kulübe zaten üyesiniz");
        }

        // 3. Zaten bekleyen istek var mı kontrol et
        if (requestRepository.existsByClubIdAndStudentIdAndStatus(clubId, studentId, MembershipRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu kulübe zaten bekleyen bir üyelik isteğiniz var");
        }

        // 4. Yeni istek oluştur
        ClubMembershipRequest request = new ClubMembershipRequest(clubId, studentId);
        if (dto != null && dto.getMessage() != null) {
            request.setMessage(dto.getMessage());
        }

        ClubMembershipRequest savedRequest = requestRepository.save(request);
        log.info("Membership request created: studentId={}, clubId={}", studentId, clubId);

        return mapToDTO(savedRequest, club, null);
    }

    /**
     * Öğrenci kendi üyelik isteklerini görüntüler.
     */
    @Transactional(readOnly = true)
    public List<MembershipRequestDTO> getMyMembershipRequests(UUID studentId) {
        List<ClubMembershipRequest> requests = requestRepository.findByStudentId(studentId);

        return requests.stream()
                .map(request -> {
                    Club club = clubRepository.findById(request.getClubId()).orElse(null);
                    return mapToDTO(request, club, null);
                })
                .collect(Collectors.toList());
    }

    /**
     * Öğrenci bekleyen üyelik isteğini iptal eder.
     */
    public void cancelMembershipRequest(UUID clubId, UUID studentId) {
        ClubMembershipRequest request = requestRepository
                .findByClubIdAndStudentIdAndStatus(clubId, studentId, MembershipRequestStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bekleyen üyelik isteği bulunamadı"));

        requestRepository.delete(request);
        log.info("Membership request cancelled: studentId={}, clubId={}", studentId, clubId);
    }

    /**
     * Kulüp başkanı bekleyen üyelik isteklerini görüntüler.
     */
    @Transactional(readOnly = true)
    public List<MembershipRequestDTO> getPendingRequests(UUID clubId, UUID officialId) {
        // Yetkili kulüp başkanı mı kontrol et
        verifyClubOfficial(clubId, officialId);

        List<ClubMembershipRequest> requests = requestRepository.findByClubIdAndStatus(clubId, MembershipRequestStatus.PENDING);
        Club club = clubRepository.findById(clubId).orElse(null);

        return requests.stream()
                .map(request -> {
                    UserSummary student = fetchUserSummary(request.getStudentId());
                    return mapToDTO(request, club, student);
                })
                .collect(Collectors.toList());
    }

    /**
     * Kulüp başkanı üyelik isteğini onaylar.
     */
    public MembershipRequestDTO approveRequest(UUID clubId, UUID requestId, UUID officialId) {
        // Yetkili kulüp başkanı mı kontrol et
        verifyClubOfficial(clubId, officialId);

        ClubMembershipRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Üyelik isteği bulunamadı"));

        // İstek bu kulübe mi ait?
        if (!request.getClubId().equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek belirtilen kulübe ait değil");
        }

        // Zaten işlenmiş mi?
        if (request.getStatus() != MembershipRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek zaten işlenmiş");
        }

        // İsteği onayla
        request.setStatus(MembershipRequestStatus.APPROVED);
        request.setProcessedDate(LocalDateTime.now());
        request.setProcessedBy(officialId);
        requestRepository.save(request);

        // Kulüp üyeliği oluştur
        ClubMembership membership = new ClubMembership(clubId, request.getStudentId(), ClubRole.ROLE_MEMBER);
        membership.setTermStartDate(LocalDateTime.now());
        membershipRepository.save(membership);

        log.info("Membership request approved: requestId={}, studentId={}, clubId={}",
                requestId, request.getStudentId(), clubId);

        // Bildirim gönder
        Club club = clubRepository.findById(clubId).orElse(null);
        sendNotification(request.getStudentId(), clubId,
                club != null ? club.getName() : "Kulüp",
                "APPROVED",
                "Üyelik isteğiniz onaylandı! Artık " + (club != null ? club.getName() : "kulüp") + " üyesisiniz.");

        return mapToDTO(request, club, null);
    }

    /**
     * Kulüp başkanı üyelik isteğini reddeder.
     */
    public MembershipRequestDTO rejectRequest(UUID clubId, UUID requestId, UUID officialId, RejectMembershipRequestDTO dto) {
        // Yetkili kulüp başkanı mı kontrol et
        verifyClubOfficial(clubId, officialId);

        ClubMembershipRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Üyelik isteği bulunamadı"));

        // İstek bu kulübe mi ait?
        if (!request.getClubId().equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek belirtilen kulübe ait değil");
        }

        // Zaten işlenmiş mi?
        if (request.getStatus() != MembershipRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu istek zaten işlenmiş");
        }

        // İsteği reddet
        request.setStatus(MembershipRequestStatus.REJECTED);
        request.setProcessedDate(LocalDateTime.now());
        request.setProcessedBy(officialId);
        if (dto != null && dto.getRejectionReason() != null) {
            request.setRejectionReason(dto.getRejectionReason());
        }
        requestRepository.save(request);

        log.info("Membership request rejected: requestId={}, studentId={}, clubId={}",
                requestId, request.getStudentId(), clubId);

        // Bildirim gönder
        Club club = clubRepository.findById(clubId).orElse(null);
        String message = "Üyelik isteğiniz reddedildi.";
        if (dto != null && dto.getRejectionReason() != null) {
            message += " Neden: " + dto.getRejectionReason();
        }
        sendNotification(request.getStudentId(), clubId,
                club != null ? club.getName() : "Kulüp",
                "REJECTED", message);

        return mapToDTO(request, club, null);
    }

    /**
     * Bir kulübün bekleyen istek sayısını döndürür.
     */
    @Transactional(readOnly = true)
    public long getPendingRequestCount(UUID clubId, UUID officialId) {
        verifyClubOfficial(clubId, officialId);
        return requestRepository.countByClubIdAndStatus(clubId, MembershipRequestStatus.PENDING);
    }

    // ==================== YARDIMCI METOTLAR ====================

    /**
     * Kullanıcının belirtilen kulübün yetkilisi olup olmadığını kontrol eder.
     */
    private void verifyClubOfficial(UUID clubId, UUID userId) {
        List<ClubMembership> memberships = membershipRepository.findByClubIdAndClubRoleAndIsActive(
                clubId, ClubRole.ROLE_CLUB_OFFICIAL, true);

        boolean isOfficial = memberships.stream()
                .anyMatch(m -> m.getStudentId().equals(userId));

        if (!isOfficial) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok");
        }
    }

    /**
     * User-service'den kullanıcı bilgilerini çeker.
     */
    private UserSummary fetchUserSummary(UUID userId) {
        try {
            return userClient.getUserById(userId);
        } catch (Exception e) {
            log.warn("Failed to fetch user summary for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * RabbitMQ ile bildirim gönderir.
     */
    private void sendNotification(UUID studentId, UUID clubId, String clubName, String status, String message) {
        try {
            MembershipRequestMessage notificationMessage = new MembershipRequestMessage(
                    studentId, clubId, clubName, status, message);

            rabbitTemplate.convertAndSend(
                    ClubRabbitMQConfig.EXCHANGE_NAME,
                    ROUTING_KEY_MEMBERSHIP_NOTIFICATION,
                    notificationMessage);

            log.info("Membership notification sent: studentId={}, status={}", studentId, status);
        } catch (Exception e) {
            log.error("Failed to send membership notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Entity'yi DTO'ya dönüştürür.
     */
    private MembershipRequestDTO mapToDTO(ClubMembershipRequest request, Club club, UserSummary student) {
        MembershipRequestDTO dto = new MembershipRequestDTO();
        dto.setId(request.getId());
        dto.setClubId(request.getClubId());
        dto.setStudentId(request.getStudentId());
        dto.setStatus(request.getStatus());
        dto.setRequestDate(request.getRequestDate());
        dto.setProcessedDate(request.getProcessedDate());
        dto.setMessage(request.getMessage());
        dto.setRejectionReason(request.getRejectionReason());

        if (club != null) {
            dto.setClubName(club.getName());
            dto.setClubLogoUrl(club.getLogoUrl());
        }

        if (student != null) {
            dto.setStudentName(student.getFullName());
            dto.setStudentEmail(student.getEmail());
        }

        return dto;
    }
}

