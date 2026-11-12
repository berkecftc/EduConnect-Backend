package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.response.ClubCreationRequestResponse;
import com.educonnect.clubservice.dto.response.FounderResponse;
import com.educonnect.clubservice.service.ClubFounderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs")
@PreAuthorize("isAuthenticated()")
public class ClubFounderController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubFounderService founderService;

    public ClubFounderController(ClubFounderService founderService) {
        this.founderService = founderService;
    }

    @GetMapping("/founding-invitations")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<ClubCreationRequestResponse>> invitations(@RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(founderService.invitationsOf(UUID.fromString(userIdHeader)));
    }

    @GetMapping("/my-creation-requests")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<ClubCreationRequestResponse>> myRequests(@RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(founderService.requestsOf(UUID.fromString(userIdHeader)));
    }

    @PostMapping("/creation-requests/{requestId}/founders/confirm")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ClubCreationRequestResponse> confirm(@PathVariable UUID requestId,
                                                               @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(founderService.respond(requestId, UUID.fromString(userIdHeader), true));
    }

    @PostMapping("/creation-requests/{requestId}/founders/decline")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ClubCreationRequestResponse> decline(@PathVariable UUID requestId,
                                                               @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(founderService.respond(requestId, UUID.fromString(userIdHeader), false));
    }

    @GetMapping("/{clubId}/founders")
    public ResponseEntity<List<FounderResponse>> founders(@PathVariable UUID clubId) {
        return ResponseEntity.ok(founderService.foundersOfClub(clubId));
    }
}
