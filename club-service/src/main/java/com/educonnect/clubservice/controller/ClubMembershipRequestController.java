package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.CreateMembershipRequestDTO;
import com.educonnect.clubservice.dto.request.RejectMembershipRequestDTO;
import com.educonnect.clubservice.dto.response.MembershipRequestDTO;
import com.educonnect.clubservice.service.ClubMembershipRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs")
public class ClubMembershipRequestController {

    private final ClubMembershipRequestService membershipRequestService;

    public ClubMembershipRequestController(ClubMembershipRequestService membershipRequestService) {
        this.membershipRequestService = membershipRequestService;
    }

    // ==================== ÖĞRENCİ ENDPOINT'LERİ ====================

    /**
     * Öğrenci bir kulübe üyelik isteği gönderir.
     * POST /api/clubs/{clubId}/membership-requests
     */
    @PostMapping("/{clubId}/membership-requests")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<MembershipRequestDTO> createMembershipRequest(
            @PathVariable UUID clubId,
            @RequestBody(required = false) CreateMembershipRequestDTO dto,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID studentId = UUID.fromString(userIdHeader);
        MembershipRequestDTO response = membershipRequestService.createMembershipRequest(clubId, studentId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Öğrenci kendi üyelik isteklerini görüntüler.
     * GET /api/clubs/my-membership-requests
     */
    @GetMapping("/my-membership-requests")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<MembershipRequestDTO>> getMyMembershipRequests(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID studentId = UUID.fromString(userIdHeader);
        List<MembershipRequestDTO> requests = membershipRequestService.getMyMembershipRequests(studentId);
        return ResponseEntity.ok(requests);
    }

    /**
     * Öğrenci bekleyen üyelik isteğini iptal eder.
     * DELETE /api/clubs/{clubId}/membership-requests
     */
    @DeleteMapping("/{clubId}/membership-requests")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> cancelMembershipRequest(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID studentId = UUID.fromString(userIdHeader);
        membershipRequestService.cancelMembershipRequest(clubId, studentId);
        return ResponseEntity.noContent().build();
    }

    // ==================== KULÜP BAŞKANI ENDPOINT'LERİ ====================

    /**
     * Kulüp başkanı bekleyen üyelik isteklerini görüntüler.
     * GET /api/clubs/{clubId}/membership-requests/pending
     */
    @GetMapping("/{clubId}/membership-requests/pending")
    @PreAuthorize("hasAnyRole('CLUB_OFFICIAL', 'ADMIN')")
    public ResponseEntity<List<MembershipRequestDTO>> getPendingRequests(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID officialId = UUID.fromString(userIdHeader);
        List<MembershipRequestDTO> requests = membershipRequestService.getPendingRequests(clubId, officialId);
        return ResponseEntity.ok(requests);
    }

    /**
     * Kulüp başkanı bekleyen üyelik isteği sayısını görüntüler.
     * GET /api/clubs/{clubId}/membership-requests/pending/count
     */
    @GetMapping("/{clubId}/membership-requests/pending/count")
    @PreAuthorize("hasAnyRole('CLUB_OFFICIAL', 'ADMIN')")
    public ResponseEntity<Map<String, Long>> getPendingRequestCount(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID officialId = UUID.fromString(userIdHeader);
        long count = membershipRequestService.getPendingRequestCount(clubId, officialId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Kulüp başkanı üyelik isteğini onaylar.
     * PUT /api/clubs/{clubId}/membership-requests/{requestId}/approve
     */
    @PutMapping("/{clubId}/membership-requests/{requestId}/approve")
    @PreAuthorize("hasAnyRole('CLUB_OFFICIAL', 'ADMIN')")
    public ResponseEntity<MembershipRequestDTO> approveRequest(
            @PathVariable UUID clubId,
            @PathVariable UUID requestId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID officialId = UUID.fromString(userIdHeader);
        MembershipRequestDTO response = membershipRequestService.approveRequest(clubId, requestId, officialId);
        return ResponseEntity.ok(response);
    }

    /**
     * Kulüp başkanı üyelik isteğini reddeder.
     * PUT /api/clubs/{clubId}/membership-requests/{requestId}/reject
     */
    @PutMapping("/{clubId}/membership-requests/{requestId}/reject")
    @PreAuthorize("hasAnyRole('CLUB_OFFICIAL', 'ADMIN')")
    public ResponseEntity<MembershipRequestDTO> rejectRequest(
            @PathVariable UUID clubId,
            @PathVariable UUID requestId,
            @RequestBody(required = false) RejectMembershipRequestDTO dto,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {

        UUID officialId = UUID.fromString(userIdHeader);
        MembershipRequestDTO response = membershipRequestService.rejectRequest(clubId, requestId, officialId, dto);
        return ResponseEntity.ok(response);
    }
}

