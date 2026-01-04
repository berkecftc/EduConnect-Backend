package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.request.CreateEventRequest;
import com.educonnect.eventservice.dto.response.EventRegistrantDTO;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')")
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

        // TODO: (Güvenlik) creatorId'nin, request.getClubId() kulübünün yetkilisi olup olmadığı kontrol edilebilir.

        Event createdEvent = eventService.createEvent(request, poster, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
    }

    // --- YENİ: Bekleyen Etkinlikleri Listele ---
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')") // Sadece Admin görebilir
    public ResponseEntity<List<Event>> getPendingEvents() {
        return ResponseEntity.ok(eventService.getPendingEvents());
    }

    // --- YENİ: Etkinliği Onayla ---
    @PostMapping("/{eventId}/approve")
    @PreAuthorize("hasRole('ADMIN')") // Sadece Admin onaylayabilir
    public ResponseEntity<Event> approveEvent(@PathVariable UUID eventId) {
        Event approvedEvent = eventService.approveEvent(eventId);
        return ResponseEntity.ok(approvedEvent);
    }

    // --- YENİ: Etkinliği Reddet ---
    @PostMapping("/{eventId}/reject")
    @PreAuthorize("hasRole('ADMIN')") // Sadece Admin reddedebilir
    public ResponseEntity<Event> rejectEvent(@PathVariable UUID eventId) {
        Event rejectedEvent = eventService.rejectEvent(eventId);
        return ResponseEntity.ok(rejectedEvent);
    }

    /**
     * QR Kod Doğrulama (Kapıdaki görevli kullanır).
     * QR kodu query param (?qrCode=xxx) veya JSON body ({"qrCode": "xxx"}) olarak gönderilebilir.
     */
    @PostMapping("/verify-qr")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')")
    public ResponseEntity<String> verifyTicket(
            @RequestParam(required = false) String qrCode,
            @RequestBody(required = false) java.util.Map<String, String> body
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
            boolean verified = eventService.verifyTicket(code);
            if (verified) {
                return ResponseEntity.ok("ACCESS GRANTED: Ticket verified successfully.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Verification failed.");
            }
        } catch (RuntimeException e) {
            // "Invalid ticket" veya "Already used" hataları
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ACCESS DENIED: " + e.getMessage());
        }
    }

    // ==================== CLUB OFFICIAL DASHBOARD ENDPOINTS ====================

    /**
     * Kulüp yetkilisinin oluşturduğu tüm etkinlikleri getirir.
     * Cache: 5 dakika TTL
     */
    @GetMapping("/my-events")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')")
    public ResponseEntity<List<Event>> getMyCreatedEvents(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID creatorId = UUID.fromString(userIdHeader);
        List<Event> events = eventService.getEventsCreatedByUser(creatorId);
        return ResponseEntity.ok(events);
    }

    /**
     * Bir etkinliğe kayıtlı tüm kullanıcıları getirir.
     * User-service'den isim/email bilgisi ile zenginleştirilmiş.
     *
     * Yetki: Sadece etkinliği oluşturan kişi, ilgili kulübün yetkilisi veya ADMIN erişebilir.
     * Cache: 5 dakika TTL
     */
    @GetMapping("/{eventId}/registrations")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')")
    public ResponseEntity<List<EventRegistrantDTO>> getEventRegistrations(
            @PathVariable UUID eventId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID requesterId = UUID.fromString(userIdHeader);
        boolean isAdmin = checkIfAdmin();

        List<EventRegistrantDTO> registrants = eventService.getEventRegistrantsWithUserInfo(eventId, requesterId, isAdmin);
        return ResponseEntity.ok(registrants);
    }

    /**
     * Bir kulübün tüm etkinliklerini getirir.
     * Kulüp yetkilisi kendi kulübünün etkinliklerini görmek için kullanır.
     */
    @GetMapping("/club/{clubId}/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')")
    public ResponseEntity<List<Event>> getClubEvents(@PathVariable UUID clubId) {
        List<Event> events = eventService.getEventsByClubId(clubId);
        return ResponseEntity.ok(events);
    }

    /**
     * SecurityContext'ten kullanıcının ADMIN rolüne sahip olup olmadığını kontrol eder.
     */
    private boolean checkIfAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));
    }

    // Etkinlik İptal Etme (DELETE) de buraya eklenebilir
}