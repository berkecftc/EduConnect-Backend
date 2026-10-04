package com.educonnect.eventservice.controller;

import com.educonnect.eventservice.dto.request.EventStaffRequest;
import com.educonnect.eventservice.dto.response.EventResponse;
import com.educonnect.eventservice.dto.response.EventStaffResponse;
import com.educonnect.eventservice.service.EventStaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/events/manage")
public class EventStaffController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final EventStaffService staffService;

    public EventStaffController(EventStaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/{eventId}/staff")
    public ResponseEntity<List<EventStaffResponse>> list(@PathVariable UUID eventId, @RequestHeader(USER_ID_HEADER) UUID userId) {
        return ResponseEntity.ok(staffService.list(eventId, userId));
    }

    @PostMapping("/{eventId}/staff")
    public ResponseEntity<EventStaffResponse> propose(@PathVariable UUID eventId, @Valid @RequestBody EventStaffRequest request,
                                                      @RequestHeader(USER_ID_HEADER) UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.propose(eventId, userId, request.userId()));
    }

    @PostMapping("/{eventId}/staff/{staffId}/approve")
    public ResponseEntity<EventStaffResponse> approve(@PathVariable UUID eventId, @PathVariable UUID staffId,
                                                      @RequestHeader(USER_ID_HEADER) UUID userId) {
        return ResponseEntity.ok(staffService.approve(eventId, staffId, userId));
    }

    @PostMapping("/{eventId}/staff/{staffId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID eventId, @PathVariable UUID staffId,
                                       @RequestHeader(USER_ID_HEADER) UUID userId) {
        staffService.reject(eventId, staffId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}/staff/{staffId}")
    public ResponseEntity<Void> remove(@PathVariable UUID eventId, @PathVariable UUID staffId,
                                       @RequestHeader(USER_ID_HEADER) UUID userId) {
        staffService.remove(eventId, staffId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staff/me")
    public ResponseEntity<List<EventResponse>> myAssignments(@RequestHeader(USER_ID_HEADER) UUID userId) {
        return ResponseEntity.ok(EventResponse.from(staffService.myAssignments(userId)));
    }
}
