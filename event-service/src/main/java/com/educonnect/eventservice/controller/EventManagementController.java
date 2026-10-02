package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.request.CreateEventRequest;
import com.educonnect.eventservice.dto.request.VerifyQrRequest;
import com.educonnect.eventservice.dto.response.EventRegistrantDTO;
import com.educonnect.eventservice.dto.response.EventResponse;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.service.EventQueryService;
import com.educonnect.eventservice.service.EventService;
import com.educonnect.common.web.ApiException;
import com.educonnect.common.web.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/events/manage") // Yönetim rotası
public class EventManagementController {

    private final EventService eventService;
    private final EventQueryService eventQueryService;

    public EventManagementController(EventService eventService, EventQueryService eventQueryService) {
        this.eventService = eventService;
        this.eventQueryService = eventQueryService;
    }

    /**
     * Yeni Etkinlik Oluşturma (Resimli).
     * RequestPart kullanıyoruz çünkü hem JSON hem Dosya aynı anda gelecek.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestPart("data") CreateEventRequest request, // JSON verisi
            @RequestPart(value = "poster", required = false) MultipartFile poster,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID creatorId = UUID.fromString(userIdHeader);

        String contentType = poster == null || poster.isEmpty() ? "image/" : poster.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("INVALID_POSTER_TYPE", "Afiş yalnızca resim dosyası olabilir.");
        }


        Event createdEvent = eventService.createEvent(request, poster, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(createdEvent));
    }

    @GetMapping("/pending")
    public ResponseEntity<String> getPendingEvents() {
        throw advisorFlowOnly();
    }

    @PostMapping("/{eventId}/approve")
    public ResponseEntity<String> approveEvent(@PathVariable UUID eventId) {
        throw advisorFlowOnly();
    }

    @PostMapping("/{eventId}/reject")
    public ResponseEntity<String> rejectEvent(@PathVariable UUID eventId) {
        throw advisorFlowOnly();
    }

    private static ApiException advisorFlowOnly() {
        return new ApiException(HttpStatus.GONE, "ENDPOINT_GONE",
                "Bu uç kapatıldı. Etkinlik onayı için /api/events/advisor/pending, /api/events/advisor/{eventId}/approve ve /reject kullanın.");
    }

    /**
     * QR Kod Doğrulama (Kapıdaki görevli kullanır).
     * QR kodu query param (?qrCode=xxx) veya JSON body ({"qrCode": "xxx"}) olarak gönderilebilir.
     */
    @PostMapping("/verify-qr")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> verifyTicket(
            @RequestParam(required = false) String qrCode,
            @Valid @RequestBody(required = false) VerifyQrRequest body,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        // QR kodu önce query param'dan, yoksa body'den al
        String code = qrCode;
        if (code == null && body != null) {
            code = body.qrCode();
        }

        if (code == null || code.isBlank()) {
            throw new BadRequestException("QR_CODE_REQUIRED", "QR code is required.");
        }

        boolean verified = eventService.verifyTicket(code, UUID.fromString(userIdHeader));
        if (!verified) {
            throw new BadRequestException("VERIFICATION_FAILED", "Verification failed.");
        }
        return ResponseEntity.ok("ACCESS GRANTED: Ticket verified successfully.");
    }

    // ==================== CLUB OFFICIAL DASHBOARD ENDPOINTS ====================

    @GetMapping("/my-events")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EventResponse>> getMyCreatedEvents(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        List<Event> events = eventQueryService.getEventsOfManagedClubs(UUID.fromString(userIdHeader));
        return ResponseEntity.ok(EventResponse.from(events));
    }

    /**
     * Bir etkinliğe kayıtlı tüm kullanıcıları getirir.
     * User-service'den isim/email bilgisi ile zenginleştirilmiş.
     *
     * Yetki: Sadece ilgili kulübün yönetim kurulu veya danışmanı erişebilir.
     */
    @GetMapping("/{eventId}/registrations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EventRegistrantDTO>> getEventRegistrations(
            @PathVariable UUID eventId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID requesterId = UUID.fromString(userIdHeader);
        List<EventRegistrantDTO> registrants = eventQueryService.getEventRegistrantsWithUserInfo(eventId, requesterId);
        return ResponseEntity.ok(registrants);
    }

    /**
     * Bir kulübün tüm etkinliklerini getirir.
     * Kulüp yetkilisi kendi kulübünün etkinliklerini görmek için kullanır.
     */
    @GetMapping("/club/{clubId}/events")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EventResponse>> getClubEvents(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        List<Event> events = eventQueryService.getEventsByClubIdForManagement(clubId, UUID.fromString(userIdHeader));
        return ResponseEntity.ok(EventResponse.from(events));
    }

    // Etkinlik İptal Etme (DELETE) de buraya eklenebilir
}
