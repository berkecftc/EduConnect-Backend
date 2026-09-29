package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.RejectionReasonRequest;
import com.educonnect.clubservice.dto.response.ClubCreationRequestResponse;
import com.educonnect.clubservice.dto.response.ClubResponse;
import com.educonnect.clubservice.service.ClubFoundingService;
import jakarta.validation.Valid;
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

    private final ClubFoundingService clubFoundingService;

    public AdvisorClubCreationController(ClubFoundingService clubFoundingService) {
        this.clubFoundingService = clubFoundingService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<List<ClubCreationRequestResponse>> getPendingRequests(
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {
        return ResponseEntity.ok(ClubCreationRequestResponse.from(clubFoundingService.getPendingCreationRequestsForAdvisor(UUID.fromString(userIdHeader))));
    }

    @PutMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<ClubResponse> approve(
            @PathVariable UUID requestId,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {
        return ResponseEntity.ok(ClubResponse.from(clubFoundingService.approveClubCreationRequestByAdvisor(requestId, UUID.fromString(userIdHeader))));
    }

    @PutMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<ClubCreationRequestResponse> reject(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) RejectionReasonRequest body,
            @RequestHeader("X-Authenticated-User-Id") String userIdHeader) {
        return ResponseEntity.ok(ClubCreationRequestResponse.from(clubFoundingService.rejectClubCreationRequestByAdvisor(
                requestId, UUID.fromString(userIdHeader), body != null ? body.rejectionReason() : null)));
    }
}
