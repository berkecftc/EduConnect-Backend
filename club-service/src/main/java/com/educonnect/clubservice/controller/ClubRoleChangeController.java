package com.educonnect.clubservice.controller;

import com.educonnect.common.web.LogValues;
import com.educonnect.clubservice.dto.request.CreateRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.request.RejectRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.request.RejectionReasonRequest;
import com.educonnect.clubservice.dto.response.RoleChangeRequestDTO;
import com.educonnect.clubservice.service.RoleChangeDecisionService;
import com.educonnect.clubservice.service.RoleChangeRequestService;
import jakarta.validation.Valid;
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
    private final RoleChangeDecisionService roleChangeDecisionService;

    public ClubRoleChangeController(RoleChangeRequestService roleChangeRequestService,
                                    RoleChangeDecisionService roleChangeDecisionService) {
        this.roleChangeRequestService = roleChangeRequestService;
        this.roleChangeDecisionService = roleChangeDecisionService;
    }

    // ==================== KULÜP YETKİLİSİ ENDPOINT'LERİ ====================

    /**
     * Görev değişikliği talebi oluşturur.
     */
    @PostMapping("/api/clubs/{clubId}/role-change-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RoleChangeRequestDTO> createRoleChangeRequest(
            @PathVariable UUID clubId,
            @Valid @RequestBody CreateRoleChangeRequestDTO request,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID requesterId = UUID.fromString(userIdHeader);
        log.info("Creating role change request: clubId={}, requesterId={}, targetStudentId={}, targetStudentNumber={}, requestedRole={}",
                clubId, LogValues.safe(requesterId), LogValues.safe(request.getStudentId()), LogValues.safe(request.getStudentNumber()), LogValues.safe(request.getRequestedRole()));

        RoleChangeRequestDTO response = roleChangeRequestService.createRoleChangeRequest(clubId, request, requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Kulübün görev değişikliği taleplerini listeler.
     */
    @GetMapping("/api/clubs/{clubId}/role-change-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RoleChangeRequestDTO>> getClubRoleChangeRequests(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID requesterId = UUID.fromString(userIdHeader);
        List<RoleChangeRequestDTO> requests = roleChangeRequestService.getClubRoleChangeRequests(clubId, requesterId);
        return ResponseEntity.ok(requests);
    }

    @DeleteMapping("/api/clubs/{clubId}/members/{studentId}/role")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> revokeRole(
            @PathVariable UUID clubId,
            @PathVariable UUID studentId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID requesterId = UUID.fromString(userIdHeader);
        log.info("Role revocation requested: clubId={}, studentId={}, requesterId={}", clubId, studentId, requesterId);

        roleChangeRequestService.requestRoleRevocation(clubId, studentId, requesterId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Görevden alma talebi danışman onayına gönderildi.");
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

        List<RoleChangeRequestDTO> requests = roleChangeDecisionService.getPendingRequestsForAdvisor(advisorId);
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

        RoleChangeRequestDTO response = roleChangeDecisionService.approveRoleChangeRequest(requestId, advisorId);
        return ResponseEntity.ok(response);
    }

    /**
     * Danışman görev değişikliği talebini reddeder.
     */
    @PutMapping("/api/academician/role-change-requests/{requestId}/reject")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<RoleChangeRequestDTO> rejectRoleChangeRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) RejectRoleChangeRequestDTO dto,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID advisorId = UUID.fromString(userIdHeader);
        log.info("Rejecting role change request: requestId={}, advisorId={}, reason={}",
                requestId, LogValues.safe(advisorId), LogValues.safe(dto != null ? dto.getRejectionReason() : "N/A"));

        RoleChangeRequestDTO response = roleChangeDecisionService.rejectRoleChangeRequest(requestId, advisorId, dto);
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
        long count = roleChangeDecisionService.getPendingRequestCountForClub(clubId, advisorId);
        return ResponseEntity.ok(count);
    }

    @DeleteMapping("/api/academician/clubs/{clubId}/president")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<String> removePresident(
            @PathVariable UUID clubId,
            @Valid @RequestBody(required = false) RejectionReasonRequest reason,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID advisorId = UUID.fromString(userIdHeader);
        log.info("Advisor removing president: clubId={}, advisorId={}", clubId, advisorId);

        roleChangeDecisionService.removePresidentByAdvisor(clubId, advisorId,
                reason != null ? reason.rejectionReason() : null);
        return ResponseEntity.ok("Kulüp başkanı görevden alındı. Başkan yardımcısı varsa kulüp başkanı olur; yoksa yeni başkanı kulübün aktif üyeleri arasından siz atayabilirsiniz.");
    }
}
