package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.MyEventRegistrationDTO;
import com.educonnect.eventservice.dto.response.EventResponse;
import com.educonnect.eventservice.dto.response.PageResponse;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.service.EventQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events") // Public/Öğrenci rotası
public class EventController {

    private final EventQueryService eventQueryService;

    public EventController(EventQueryService eventQueryService) {
        this.eventQueryService = eventQueryService;
    }

    /**
     * Aktif tüm etkinlikleri listeler.
     */
    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllActiveEvents() {
        return ResponseEntity.ok(EventResponse.from(eventQueryService.getAllActiveEvents()));
    }

    @GetMapping(params = "page")
    public ResponseEntity<PageResponse<EventResponse>> getActiveEventsPage(@RequestParam int page,
                                                                            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(eventQueryService.getActiveEventsPage(page, size).map(EventResponse::from));
    }

    // 👇 ADMİN İÇİN ÖZEL ENDPOINT
    // Bu endpoint Bekleyen, Onaylanan, Reddedilen, Geçmiş... HEPSİNİ getirir.
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EventResponse>> getAllEventsForAdmin() {
        return ResponseEntity.ok(EventResponse.from(eventQueryService.getAllEventsForAdmin()));
    }

    /**
     * Tek bir etkinliğin detaylarını getirir.
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEventDetails(
            @PathVariable UUID eventId,
            @RequestHeader(value = "X-Authenticated-User-Id", required = false) String userIdHeader
    ) {
        UUID viewerId = userIdHeader != null ? UUID.fromString(userIdHeader) : null;
        return ResponseEntity.ok(EventResponse.from(eventQueryService.getEventDetailsForViewer(eventId, viewerId)));
    }

    /**
     * Öğrenci: Kayıtlı Olduğu Tüm Etkinlikleri Getir
     */
    @GetMapping("/my-registrations")
    public ResponseEntity<List<MyEventRegistrationDTO>> getMyRegistrations(
            @RequestHeader("X-Authenticated-User-Id") String studentIdHeader
    ) {
        UUID studentId = UUID.fromString(studentIdHeader);
        return ResponseEntity.ok(eventQueryService.getStudentEventRegistrations(studentId));
    }

    /**
     * Bir kulübün aktif etkinliklerini getirir.
     * Öğrenciler üye oldukları kulübün etkinliklerini görmek için kullanır.
     */
    @GetMapping("/club/{clubId}")
    public ResponseEntity<List<EventResponse>> getClubEvents(@PathVariable UUID clubId) {
        List<Event> events = eventQueryService.getEventsByClubId(clubId);
        // Sadece aktif etkinlikleri filtrele (öğrenciler için)
        List<Event> activeEvents = events.stream()
                .filter(e -> e.getStatus() == com.educonnect.eventservice.model.EventStatus.ACTIVE)
                .toList();
        return ResponseEntity.ok(EventResponse.from(activeEvents));
    }
}
