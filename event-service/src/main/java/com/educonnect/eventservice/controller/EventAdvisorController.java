package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Akademisyen (Danışman Hoca) için etkinlik yönetim endpoint'leri.
 * Frontend'in /api/events/advisor/* yollarına yaptığı istekleri karşılar.
 */
@RestController
@RequestMapping("/api/events/advisor")
public class EventAdvisorController {

    private final EventService eventService;

    public EventAdvisorController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Danışman akademisyenin sorumlu olduğu kulüplerin TÜM etkinliklerini listeler.
     * GET /api/events/advisor/all
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<List<Event>> getAllEventsForAdvisor(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID advisorId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(eventService.getAllEventsForAdvisor(advisorId));
    }

    /**
     * Danışman akademisyenin sorumlu olduğu kulüplerin bekleyen etkinliklerini listeler.
     * GET /api/events/advisor/pending
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<List<Event>> getPendingEvents(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID advisorId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(eventService.getPendingEventsForAdvisor(advisorId));
    }

    /**
     * Danışman akademisyen etkinliği onaylar.
     * POST /api/events/advisor/{eventId}/approve
     */
    @PostMapping("/{eventId}/approve")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<Event> approveEvent(
            @PathVariable UUID eventId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID approverId = UUID.fromString(userIdHeader);
        Event approvedEvent = eventService.approveEvent(eventId, approverId);
        return ResponseEntity.ok(approvedEvent);
    }

    /**
     * Danışman akademisyen etkinliği reddeder.
     * POST /api/events/advisor/{eventId}/reject
     */
    @PostMapping("/{eventId}/reject")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<Event> rejectEvent(
            @PathVariable UUID eventId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader
    ) {
        UUID rejectorId = UUID.fromString(userIdHeader);
        Event rejectedEvent = eventService.rejectEvent(eventId, rejectorId);
        return ResponseEntity.ok(rejectedEvent);
    }
}


