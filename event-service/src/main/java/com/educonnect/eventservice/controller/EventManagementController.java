package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.request.CreateEventRequest;
import com.educonnect.eventservice.dto.response.EventRegistrantDTO;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/events/manage") // Yönetim rotası
public class EventManagementController {

    private final EventService eventService;

    public EventManagementController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Yeni Etkinlik Oluşturma (Resimli).
     * RequestPart kullanıyoruz çünkü hem JSON hem Dosya aynı anda gelecek.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Event> createEvent(
            @RequestPart("data") CreateEventRequest request, // JSON verisi
            @RequestPart(value = "poster") MultipartFile poster, // Afiş dosyası (zorunlu)
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID creatorId = UUID.fromString(userIdHeader);

        // Afiş dosyası boş olamaz
        if (poster == null || poster.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Dosya türü kontrolü - sadece resim dosyaları kabul edilir
        String contentType = poster.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().build();
        }


        Event createdEvent = eventService.createEvent(request, poster, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
    }

    @GetMapping("/pending")
    public ResponseEntity<String> getPendingEvents() {
        return advisorFlowOnly();
    }

    @PostMapping("/{eventId}/approve")
    public ResponseEntity<String> approveEvent(@PathVariable UUID eventId) {
        return advisorFlowOnly();
    }

    @PostMapping("/{eventId}/reject")
    public ResponseEntity<String> rejectEvent(@PathVariable UUID eventId) {
        return advisorFlowOnly();
    }

    private static ResponseEntity<String> advisorFlowOnly() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body("Bu uç kapatıldı. Etkinlik onayı için /api/events/advisor/pending, /api/events/advisor/{eventId}/approve ve /reject kullanın.");
    }

    /**
     * QR Kod Doğrulama (Kapıdaki görevli kullanır).
     * QR kodu query param (?qrCode=xxx) veya JSON body ({"qrCode": "xxx"}) olarak gönderilebilir.
     */
    @PostMapping("/verify-qr")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> verifyTicket(
            @RequestParam(required = false) String qrCode,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        // QR kodu önce query param'dan, yoksa body'den al
        String code = qrCode;
        if (code == null && body != null) {
            code = body.get("qrCode");
        }

        if (code == null || code.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("QR code is required.");
        }

        try {
            boolean verified = eventService.verifyTicket(code, UUID.fromString(userIdHeader));
            if (verified) {
                return ResponseEntity.ok("ACCESS GRANTED: Ticket verified successfully.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Verification failed.");
            }
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body("ACCESS DENIED: " + e.getReason());
        } catch (RuntimeException e) {
            // "Invalid ticket" veya "Already used" hataları
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ACCESS DENIED: " + e.getMessage());
        }
    }

    // ==================== CLUB OFFICIAL DASHBOARD ENDPOINTS ====================

    @GetMapping("/my-events")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Event>> getMyCreatedEvents(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        List<Event> events = eventService.getEventsOfManagedClubs(UUID.fromString(userIdHeader));
        return ResponseEntity.ok(events);
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
        List<EventRegistrantDTO> registrants = eventService.getEventRegistrantsWithUserInfo(eventId, requesterId);
        return ResponseEntity.ok(registrants);
    }

    /**
     * Bir kulübün tüm etkinliklerini getirir.
     * Kulüp yetkilisi kendi kulübünün etkinliklerini görmek için kullanır.
     */
    @GetMapping("/club/{clubId}/events")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Event>> getClubEvents(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        List<Event> events = eventService.getEventsByClubIdForManagement(clubId, UUID.fromString(userIdHeader));
        return ResponseEntity.ok(events);
    }

    // Etkinlik İptal Etme (DELETE) de buraya eklenebilir
}