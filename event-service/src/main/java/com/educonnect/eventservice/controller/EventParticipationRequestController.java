package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.request.CreateParticipationRequestDTO;
import com.educonnect.eventservice.dto.response.EventParticipationRequestDTO;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.service.EventParticipationRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Etkinlik katılım istekleri için controller.
 * Öğrenciler katılım isteği gönderir, kulüp yetkilileri onaylar/reddeder.
 */
@RestController
@RequestMapping("/api/events")
public class EventParticipationRequestController {

    private final EventParticipationRequestService participationRequestService;

    public EventParticipationRequestController(EventParticipationRequestService participationRequestService) {
        this.participationRequestService = participationRequestService;
    }

    // ==================== ÖĞRENCİ ENDPOINT'LERİ ====================

    /**
     * Öğrenci: Etkinliğe katılım isteği gönder.
     * POST /api/events/{eventId}/participation-request
     */
    @PostMapping("/{eventId}/participation-request")
    public ResponseEntity<?> createParticipationRequest(
            @PathVariable UUID eventId,
            @RequestBody(required = false) CreateParticipationRequestDTO requestDTO,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        try {
            UUID studentId = UUID.fromString(userIdHeader);
            String message = requestDTO != null ? requestDTO.getMessage() : null;

            EventParticipationRequest request = participationRequestService
                    .createParticipationRequest(eventId, studentId, message);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Katılım isteğiniz alındı. Kulüp yetkilisi onayladıktan sonra biletiniz e-posta adresinize gönderilecektir.",
                    "requestId", request.getId(),
                    "status", request.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Öğrenci: Kendi katılım isteklerini görüntüle.
     * GET /api/events/my-participation-requests
     */
    @GetMapping("/my-participation-requests")
    public ResponseEntity<List<EventParticipationRequestDTO>> getMyParticipationRequests(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID studentId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(participationRequestService.getStudentParticipationRequests(studentId));
    }

    // ==================== KULÜP YETKİLİSİ ENDPOINT'LERİ ====================

    /**
     * Kulüp Yetkilisi: Bir etkinliğin bekleyen katılım isteklerini görüntüle.
     * GET /api/events/{eventId}/participation-requests/pending
     */
    @GetMapping("/{eventId}/participation-requests/pending")
    public ResponseEntity<List<EventParticipationRequestDTO>> getPendingRequestsForEvent(
            @PathVariable UUID eventId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID requesterId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(participationRequestService.getPendingRequestsForEvent(eventId, requesterId));
    }

    /**
     * Kulüp Yetkilisi: Bir etkinliğin tüm katılım isteklerini görüntüle.
     * GET /api/events/{eventId}/participation-requests
     */
    @GetMapping("/{eventId}/participation-requests")
    public ResponseEntity<List<EventParticipationRequestDTO>> getAllRequestsForEvent(
            @PathVariable UUID eventId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID requesterId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(participationRequestService.getAllRequestsForEvent(eventId, requesterId));
    }

    /**
     * Kulüp Yetkilisi: Yönettiği tüm etkinliklerin bekleyen isteklerini görüntüle.
     * GET /api/events/official/pending-requests
     */
    @GetMapping("/official/pending-requests")
    public ResponseEntity<List<EventParticipationRequestDTO>> getPendingRequestsForOfficialEvents(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID officialId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(participationRequestService.getPendingRequestsForOfficialEvents(officialId));
    }

    /**
     * Kulüp Yetkilisi: Katılım isteğini onayla.
     * POST /api/events/participation-requests/{requestId}/approve
     */
    @PostMapping("/participation-requests/{requestId}/approve")
    public ResponseEntity<?> approveParticipationRequest(
            @PathVariable UUID requestId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        try {
            UUID approverId = UUID.fromString(userIdHeader);
            EventRegistration registration = participationRequestService
                    .approveParticipationRequest(requestId, approverId);

            return ResponseEntity.ok(Map.of(
                    "message", "Katılım isteği onaylandı. Öğrenciye QR kodlu bilet e-posta ile gönderildi.",
                    "registrationId", registration.getId(),
                    "qrCode", registration.getQrCode()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Kulüp Yetkilisi: Katılım isteğini reddet.
     * POST /api/events/participation-requests/{requestId}/reject
     */
    @PostMapping("/participation-requests/{requestId}/reject")
    public ResponseEntity<?> rejectParticipationRequest(
            @PathVariable UUID requestId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        try {
            UUID rejecterId = UUID.fromString(userIdHeader);
            String rejectionReason = body != null ? body.get("reason") : null;

            EventParticipationRequest request = participationRequestService
                    .rejectParticipationRequest(requestId, rejecterId, rejectionReason);

            return ResponseEntity.ok(Map.of(
                    "message", "Katılım isteği reddedildi.",
                    "requestId", request.getId(),
                    "status", request.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}

