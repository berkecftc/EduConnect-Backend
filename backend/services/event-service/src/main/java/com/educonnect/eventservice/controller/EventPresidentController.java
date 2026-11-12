package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.request.EventDecisionReason;
import com.educonnect.eventservice.dto.response.EventResponse;
import com.educonnect.eventservice.service.EventPresidentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/president")
@PreAuthorize("isAuthenticated()")
public class EventPresidentController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final EventPresidentService presidentService;

    public EventPresidentController(EventPresidentService presidentService) {
        this.presidentService = presidentService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<EventResponse>> pending(@RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(EventResponse.from(presidentService.getPendingEventsForPresident(UUID.fromString(userIdHeader))));
    }

    @PostMapping("/{eventId}/approve")
    public ResponseEntity<EventResponse> approve(@PathVariable UUID eventId,
                                                 @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(EventResponse.from(presidentService.approve(eventId, UUID.fromString(userIdHeader))));
    }

    @PostMapping("/{eventId}/reject")
    public ResponseEntity<EventResponse> reject(@PathVariable UUID eventId,
                                                @Valid @RequestBody EventDecisionReason request,
                                                @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(EventResponse.from(presidentService.reject(eventId, UUID.fromString(userIdHeader), request.reason())));
    }
}
