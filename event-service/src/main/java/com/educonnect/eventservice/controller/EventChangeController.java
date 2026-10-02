package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.request.EventChangeRequest;
import com.educonnect.eventservice.dto.request.UpdateEventRequest;
import com.educonnect.eventservice.dto.response.EventChangeResponse;
import com.educonnect.eventservice.dto.response.EventResponse;
import com.educonnect.eventservice.service.EventChangeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/manage/{eventId}")
public class EventChangeController {

    private final EventChangeService changeService;

    public EventChangeController(EventChangeService changeService) {
        this.changeService = changeService;
    }

    @PutMapping
    public ResponseEntity<EventResponse> update(@PathVariable UUID eventId,
                                                @Valid @RequestBody UpdateEventRequest request,
                                                @RequestHeader("X-Authenticated-User-Id") UUID actorId) {
        return ResponseEntity.ok(EventResponse.from(changeService.update(eventId, actorId, request)));
    }

    @PostMapping("/postpone")
    public ResponseEntity<EventResponse> postpone(@PathVariable UUID eventId,
                                                  @Valid @RequestBody EventChangeRequest request,
                                                  @RequestHeader("X-Authenticated-User-Id") UUID actorId) {
        return ResponseEntity.ok(EventResponse.from(changeService.postpone(eventId, actorId, request)));
    }

    @PostMapping("/relocate")
    public ResponseEntity<EventResponse> relocate(@PathVariable UUID eventId,
                                                  @Valid @RequestBody EventChangeRequest request,
                                                  @RequestHeader("X-Authenticated-User-Id") UUID actorId) {
        return ResponseEntity.ok(EventResponse.from(changeService.relocate(eventId, actorId, request)));
    }

    @PostMapping("/cancel")
    public ResponseEntity<EventResponse> cancel(@PathVariable UUID eventId,
                                                @Valid @RequestBody EventChangeRequest request,
                                                @RequestHeader("X-Authenticated-User-Id") UUID actorId) {
        return ResponseEntity.ok(EventResponse.from(changeService.cancel(eventId, actorId, request)));
    }

    @GetMapping("/changes")
    public ResponseEntity<List<EventChangeResponse>> changes(@PathVariable UUID eventId,
                                                             @RequestHeader("X-Authenticated-User-Id") UUID viewerId) {
        return ResponseEntity.ok(changeService.changes(eventId, viewerId));
    }
}
