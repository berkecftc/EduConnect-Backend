package com.educonnect.notificationservice.controller;

import com.educonnect.notificationservice.dto.request.PreferenceRequest;
import com.educonnect.notificationservice.dto.response.NotificationResponse;
import com.educonnect.notificationservice.dto.response.PreferenceResponse;
import com.educonnect.notificationservice.service.InboxService;
import com.educonnect.notificationservice.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final String USER_ID = "X-Authenticated-User-Id";

    private final InboxService inboxService;
    private final PreferenceService preferenceService;

    public NotificationController(InboxService inboxService, PreferenceService preferenceService) {
        this.inboxService = inboxService;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> inbox(
            @RequestHeader(USER_ID) UUID userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(inboxService.inbox(userId, unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@RequestHeader(USER_ID) UUID userId) {
        return ResponseEntity.ok(Map.of("unread", inboxService.unreadCount(userId)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(@RequestHeader(USER_ID) UUID userId,
                                                         @PathVariable UUID notificationId) {
        return ResponseEntity.ok(inboxService.markRead(userId, notificationId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@RequestHeader(USER_ID) UUID userId) {
        return ResponseEntity.ok(Map.of("updated", inboxService.markAllRead(userId)));
    }

    @GetMapping("/preferences")
    public ResponseEntity<List<PreferenceResponse>> preferences(@RequestHeader(USER_ID) UUID userId) {
        return ResponseEntity.ok(preferenceService.list(userId));
    }

    @PutMapping("/preferences")
    public ResponseEntity<PreferenceResponse> updatePreference(@RequestHeader(USER_ID) UUID userId,
                                                               @RequestBody @Valid PreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.update(userId, request.category(), request.emailEnabled()));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<PreferenceResponse> unsubscribe(@RequestParam(required = false) String token,
                                                          @RequestBody(required = false) Map<String, String> body) {
        String value = token != null ? token : body != null ? body.get("token") : null;
        return ResponseEntity.ok(preferenceService.unsubscribe(value));
    }
}
