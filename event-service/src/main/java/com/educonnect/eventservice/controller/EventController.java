package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.MyEventRegistrationDTO;
import com.educonnect.eventservice.dto.response.PageResponse;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events") // Public/Öğrenci rotası
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Aktif tüm etkinlikleri listeler.
     */
    @GetMapping
    public ResponseEntity<List<Event>> getAllActiveEvents() {
        return ResponseEntity.ok(eventService.getAllActiveEvents());
    }

    @GetMapping(params = "page")
    public ResponseEntity<PageResponse<Event>> getActiveEventsPage(@RequestParam int page,
                                                                  @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(eventService.getActiveEventsPage(page, size));
    }

    // 👇 ADMİN İÇİN ÖZEL ENDPOINT
    // Bu endpoint Bekleyen, Onaylanan, Reddedilen, Geçmiş... HEPSİNİ getirir.
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Event>> getAllEventsForAdmin() {
        return ResponseEntity.ok(eventService.getAllEventsForAdmin());
    }

    /**
     * Tek bir etkinliğin detaylarını getirir.
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<Event> getEventDetails(
            @PathVariable UUID eventId,
            @RequestHeader(value = "X-Authenticated-User-Id", required = false) String userIdHeader
    ) {
        UUID viewerId = userIdHeader != null ? UUID.fromString(userIdHeader) : null;
        return ResponseEntity.ok(eventService.getEventDetailsForViewer(eventId, viewerId));
    }

    /**
     * Öğrenci: Etkinliğe Kayıt Ol (Bilet Al).
     */
    @PostMapping("/{eventId}/register")
    public ResponseEntity<?> registerForEvent(
            @PathVariable UUID eventId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        try {
            UUID studentId = UUID.fromString(userIdHeader);
            EventRegistration registration = eventService.registerForEvent(eventId, studentId);

            // Başarılı kayıtta bilet bilgisini (QR kod stringini) dönüyoruz
            return ResponseEntity.status(HttpStatus.CREATED).body(registration);

        } catch (IllegalStateException e) {
            // "Zaten kayıtlı" veya "İptal edilmiş" hatası
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Registration failed.");
        }
    }

    /**
     * Öğrenci: Kayıtlı Olduğu Tüm Etkinlikleri Getir
     */
    @GetMapping("/my-registrations")
    public ResponseEntity<List<MyEventRegistrationDTO>> getMyRegistrations(
            @RequestHeader("X-Authenticated-User-Id") String studentIdHeader
    ) {
        UUID studentId = UUID.fromString(studentIdHeader);
        return ResponseEntity.ok(eventService.getStudentEventRegistrations(studentId));
    }

    /**
     * Bir kulübün aktif etkinliklerini getirir.
     * Öğrenciler üye oldukları kulübün etkinliklerini görmek için kullanır.
     */
    @GetMapping("/club/{clubId}")
    public ResponseEntity<List<Event>> getClubEvents(@PathVariable UUID clubId) {
        List<Event> events = eventService.getEventsByClubId(clubId);
        // Sadece aktif etkinlikleri filtrele (öğrenciler için)
        List<Event> activeEvents = events.stream()
                .filter(e -> e.getStatus() == com.educonnect.eventservice.model.EventStatus.ACTIVE)
                .toList();
        return ResponseEntity.ok(activeEvents);
    }
}

