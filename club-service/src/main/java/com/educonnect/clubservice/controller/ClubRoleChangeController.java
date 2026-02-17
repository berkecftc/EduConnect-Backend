package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.CreateRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.request.RejectRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.response.RoleChangeRequestDTO;
import com.educonnect.clubservice.service.RoleChangeRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Kulüp görev değişikliği talepleri için controller.
 *
 * Kulüp Yetkilileri için:
 * - POST /api/clubs/{clubId}/role-change-requests : Görev değişikliği talebi oluştur
 * - GET /api/clubs/{clubId}/role-change-requests : Kulübün taleplerini görüntüle
 * - DELETE /api/clubs/{clubId}/members/{studentId}/role : Üyeyi görevden al
 *
 * Akademisyenler (Danışmanlar) için:
 * - GET /api/academician/role-change-requests : Bekleyen talepleri görüntüle
 * - PUT /api/academician/role-change-requests/{requestId}/approve : Talebi onayla
 * - PUT /api/academician/role-change-requests/{requestId}/reject : Talebi reddet
 */
@RestController
public class ClubRoleChangeController {

    private static final Logger log = LoggerFactory.getLogger(ClubRoleChangeController.class);

    private final RoleChangeRequestService roleChangeRequestService;

    public ClubRoleChangeController(RoleChangeRequestService roleChangeRequestService) {
        this.roleChangeRequestService = roleChangeRequestService;
    }

    // ==================== KULÜP YETKİLİSİ ENDPOINT'LERİ ====================

    /**
     * Görev değişikliği talebi oluşturur.
     * Sadece kulüp başkanı, başkan yardımcısı veya YK üyesi yapabilir.
     */
    @PostMapping("/api/clubs/{clubId}/role-change-requests")
    @PreAuthorize("hasAnyRole('CLUB_OFFICIAL', 'ADMIN')")
    public ResponseEntity<RoleChangeRequestDTO> createRoleChangeRequest(
            @PathVariable UUID clubId,
            @RequestBody CreateRoleChangeRequestDTO request,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID requesterId = UUID.fromString(userIdHeader);
        log.info("Creating role change request: clubId={}, requesterId={}, targetStudentId={}, targetStudentNumber={}, requestedRole={}",
                clubId, requesterId, request.getStudentId(), request.getStudentNumber(), request.getRequestedRole());

        RoleChangeRequestDTO response = roleChangeRequestService.createRoleChangeRequest(clubId, request, requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Kulübün görev değişikliği taleplerini listeler.
     */
    @GetMapping("/api/clubs/{clubId}/role-change-requests")
    @PreAuthorize("hasAnyRole('CLUB_OFFICIAL', 'ADMIN')")
    public ResponseEntity<List<RoleChangeRequestDTO>> getClubRoleChangeRequests(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID requesterId = UUID.fromString(userIdHeader);
        List<RoleChangeRequestDTO> requests = roleChangeRequestService.getClubRoleChangeRequests(clubId, requesterId);
        return ResponseEntity.ok(requests);
    }

    /**
     * Bir üyeyi görevden alır (rolünü normal üye yapar).
     * Bu işlem onay gerektirmez, doğrudan uygulanır.
     */
    @DeleteMapping("/api/clubs/{clubId}/members/{studentId}/role")
    @PreAuthorize("hasAnyRole('CLUB_OFFICIAL', 'ADMIN')")
    public ResponseEntity<String> revokeRole(
            @PathVariable UUID clubId,
            @PathVariable UUID studentId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID requesterId = UUID.fromString(userIdHeader);
        log.info("Revoking role: clubId={}, studentId={}, requesterId={}", clubId, studentId, requesterId);

        roleChangeRequestService.revokeRole(clubId, studentId, requesterId);
        return ResponseEntity.ok("Üye görevden başarıyla alındı.");
    }

    // ==================== AKADEMİSYEN (DANIŞMAN) ENDPOINT'LERİ ====================

    /**
     * Danışmanın sorumlu olduğu kulüplerin bekleyen görev değişikliği taleplerini listeler.
     */
    @GetMapping("/api/academician/role-change-requests")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<List<RoleChangeRequestDTO>> getPendingRequestsForAdvisor(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID advisorId = UUID.fromString(userIdHeader);
        log.info("Fetching pending role change requests for advisor: {}", advisorId);

        List<RoleChangeRequestDTO> requests = roleChangeRequestService.getPendingRequestsForAdvisor(advisorId);
        return ResponseEntity.ok(requests);
    }

    /**
     * Danışman görev değişikliği talebini onaylar.
     */
    @PutMapping("/api/academician/role-change-requests/{requestId}/approve")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<RoleChangeRequestDTO> approveRoleChangeRequest(
            @PathVariable UUID requestId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID advisorId = UUID.fromString(userIdHeader);
        log.info("Approving role change request: requestId={}, advisorId={}", requestId, advisorId);

        RoleChangeRequestDTO response = roleChangeRequestService.approveRoleChangeRequest(requestId, advisorId);
        return ResponseEntity.ok(response);
    }

    /**
     * Danışman görev değişikliği talebini reddeder.
     */
    @PutMapping("/api/academician/role-change-requests/{requestId}/reject")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<RoleChangeRequestDTO> rejectRoleChangeRequest(
            @PathVariable UUID requestId,
            @RequestBody(required = false) RejectRoleChangeRequestDTO dto,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID advisorId = UUID.fromString(userIdHeader);
        log.info("Rejecting role change request: requestId={}, advisorId={}, reason={}",
                requestId, advisorId, dto != null ? dto.getRejectionReason() : "N/A");

        RoleChangeRequestDTO response = roleChangeRequestService.rejectRoleChangeRequest(requestId, advisorId, dto);
        return ResponseEntity.ok(response);
    }

    /**
     * Belirli bir kulübün bekleyen talep sayısını döndürür.
     */
    @GetMapping("/api/academician/clubs/{clubId}/role-change-requests/count")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<Long> getPendingRequestCount(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID advisorId = UUID.fromString(userIdHeader);
        long count = roleChangeRequestService.getPendingRequestCountForClub(clubId, advisorId);
        return ResponseEntity.ok(count);
    }
}

