package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.request.CreateParticipationRequestDTO;
import com.educonnect.eventservice.dto.request.RejectParticipationRequestDTO;
import com.educonnect.eventservice.dto.response.EventAvailability;
import com.educonnect.eventservice.dto.response.EventParticipationRequestDTO;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.service.EventParticipationRequestQueryService;
import com.educonnect.eventservice.service.EventParticipationRequestService;
import jakarta.validation.Valid;
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
    private final EventParticipationRequestQueryService participationRequestQueryService;

    public EventParticipationRequestController(EventParticipationRequestService participationRequestService,
                                               EventParticipationRequestQueryService participationRequestQueryService) {
        this.participationRequestService = participationRequestService;
        this.participationRequestQueryService = participationRequestQueryService;
    }

    // ==================== ÖĞRENCİ ENDPOINT'LERİ ====================

    /**
     * Öğrenci: Etkinliğe katılım isteği gönder.
     * POST /api/events/{eventId}/participation-request
     */
    @PostMapping("/{eventId}/participation-request")
    public ResponseEntity<?> createParticipationRequest(
            @PathVariable UUID eventId,
            @Valid @RequestBody(required = false) CreateParticipationRequestDTO requestDTO,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader,
            @RequestHeader(value = "X-Authenticated-User-Roles", required = false) String roles
    ) {
        UUID studentId = UUID.fromString(userIdHeader);
        String message = requestDTO != null ? requestDTO.getMessage() : null;

        EventParticipationRequest request = participationRequestService
                .createParticipationRequest(eventId, studentId, roles, message);

        String text = switch (request.getStatus()) {
            case APPROVED -> "Kaydınız tamamlandı. Biletiniz e-posta adresinize gönderilecektir.";
            case WAITLISTED -> "Kontenjan dolu; bekleme listesine alındınız. Yer açılınca kaydınız otomatik yapılır.";
            default -> "Katılım isteğiniz alındı. Kulüp yetkilisi onayladıktan sonra biletiniz e-posta adresinize gönderilecektir.";
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", text,
                "requestId", request.getId(),
                "status", request.getStatus()
        ));
    }

    @DeleteMapping("/{eventId}/participation-request")
    public ResponseEntity<Void> withdrawParticipationRequest(@PathVariable UUID eventId,
                                                             @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {
        participationRequestService.withdrawRequest(eventId, UUID.fromString(userIdHeader));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}/registration")
    public ResponseEntity<Void> cancelRegistration(@PathVariable UUID eventId,
                                                   @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {
        participationRequestService.cancelRegistration(eventId, UUID.fromString(userIdHeader));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{eventId}/availability")
    public ResponseEntity<EventAvailability> availability(@PathVariable UUID eventId) {
        return ResponseEntity.ok(participationRequestService.availability(eventId));
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
        return ResponseEntity.ok(participationRequestQueryService.getStudentParticipationRequests(studentId));
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
        return ResponseEntity.ok(participationRequestQueryService.getPendingRequestsForEvent(eventId, requesterId));
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
        return ResponseEntity.ok(participationRequestQueryService.getAllRequestsForEvent(eventId, requesterId));
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
        return ResponseEntity.ok(participationRequestQueryService.getPendingRequestsForOfficialEvents(officialId));
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
        UUID approverId = UUID.fromString(userIdHeader);
        EventRegistration registration = participationRequestService
                .approveParticipationRequest(requestId, approverId);

        return ResponseEntity.ok(Map.of(
                "message", "Katılım isteği onaylandı. Öğrenciye QR kodlu bilet e-posta ile gönderildi.",
                "registrationId", registration.getId(),
                "qrCode", registration.getQrCode()
        ));
    }

    /**
     * Kulüp Yetkilisi: Katılım isteğini reddet.
     * POST /api/events/participation-requests/{requestId}/reject
     */
    @PostMapping("/participation-requests/{requestId}/reject")
    public ResponseEntity<?> rejectParticipationRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) RejectParticipationRequestDTO body,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID rejecterId = UUID.fromString(userIdHeader);
        String rejectionReason = body != null ? body.reason() : null;

        EventParticipationRequest request = participationRequestService
                .rejectParticipationRequest(requestId, rejecterId, rejectionReason);

        return ResponseEntity.ok(Map.of(
                "message", "Katılım isteği reddedildi.",
                "requestId", request.getId(),
                "status", request.getStatus()
        ));
    }
}
