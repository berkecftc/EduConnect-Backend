package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events") // Public/Öğrenci rotası
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * Aktif tüm etkinlikleri listeler.
     */
    @GetMapping
    public ResponseEntity<List<Event>> getAllActiveEvents() {
        return ResponseEntity.ok(eventService.getAllActiveEvents());
    }

    // 👇 ADMİN İÇİN ÖZEL ENDPOINT
    // Bu endpoint Bekleyen, Onaylanan, Reddedilen, Geçmiş... HEPSİNİ getirir.
    @GetMapping("/admin/all")
    public ResponseEntity<List<Event>> getAllEventsForAdmin() {
        return ResponseEntity.ok(eventService.getAllEventsForAdmin());
    }

    /**
     * Tek bir etkinliğin detaylarını getirir.
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<Event> getEventDetails(@PathVariable UUID eventId) {
        return ResponseEntity.ok(eventService.getEventDetails(eventId));
    }

    /**
     * Öğrenci: Etkinliğe Kayıt Ol (Bilet Al).
     */
    @PostMapping("/{eventId}/register")
    @PreAuthorize("hasRole('STUDENT')") // Sadece öğrenciler kaydolabilir
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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Registration failed.");
        }
    }
}