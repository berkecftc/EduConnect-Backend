package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.request.CreateEventRequest;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.service.EventService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class EventManagementController {

    private final EventService eventService;

    /**
     * Yeni Etkinlik Oluşturma (Resimli).
     * RequestPart kullanıyoruz çünkü hem JSON hem Dosya aynı anda gelecek.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')")
    public ResponseEntity<Event> createEvent(
            @RequestPart("data") CreateEventRequest request, // JSON verisi
            @RequestPart(value = "image", required = false) MultipartFile image, // Resim dosyası
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID creatorId = UUID.fromString(userIdHeader);
        // TODO: (Güvenlik) creatorId'nin, request.getClubId() kulübünün yetkilisi olup olmadığı kontrol edilebilir.

        Event createdEvent = eventService.createEvent(request, image, creatorId);
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
     */
    @PostMapping("/verify-qr")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLUB_OFFICIAL')")
    public ResponseEntity<String> verifyTicket(@RequestParam String qrCode) {
        try {
            boolean verified = eventService.verifyTicket(qrCode);
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



    // Etkinlik İptal Etme (DELETE) de buraya eklenebilir
}