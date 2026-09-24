package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.RejectRoleChangeRequestDTO;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCreationRequest;
import com.educonnect.clubservice.service.ClubService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/academician/club-creation-requests")
public class AdvisorClubCreationController {

    private final ClubService clubService;

    public AdvisorClubCreationController(ClubService clubService) {
        this.clubService = clubService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<List<ClubCreationRequest>> getPendingRequests(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {
        return ResponseEntity.ok(clubService.getPendingCreationRequestsForAdvisor(UUID.fromString(userIdHeader)));
    }

    @PutMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<Club> approve(
            @PathVariable UUID requestId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {
        return ResponseEntity.ok(clubService.approveClubCreationRequestByAdvisor(requestId, UUID.fromString(userIdHeader)));
    }

    @PutMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<ClubCreationRequest> reject(
            @PathVariable UUID requestId,
            @RequestBody(required = false) RejectRoleChangeRequestDTO body,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {
        return ResponseEntity.ok(clubService.rejectClubCreationRequestByAdvisor(requestId, UUID.fromString(userIdHeader),
                body != null ? body.getRejectionReason() : null));
    }
}
