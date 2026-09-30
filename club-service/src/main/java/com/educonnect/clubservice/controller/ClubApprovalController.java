package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.NoteRequest;
import com.educonnect.clubservice.dto.request.ReasonRequest;
import com.educonnect.clubservice.dto.response.ApprovalRequestResponse;
import com.educonnect.clubservice.dto.response.DecisionLogEntryResponse;
import com.educonnect.clubservice.service.ClubGovernanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/clubs")
@PreAuthorize("isAuthenticated()")
public class ClubApprovalController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubGovernanceService governanceService;

    public ClubApprovalController(ClubGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @GetMapping("/approvals/inbox")
    public ResponseEntity<List<ApprovalRequestResponse>> inbox(@RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(governanceService.inboxOf(UUID.fromString(userIdHeader)));
    }

    @GetMapping("/{clubId}/approvals")
    public ResponseEntity<List<ApprovalRequestResponse>> clubRequests(@PathVariable UUID clubId,
                                                                      @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(governanceService.requestsOf(clubId, UUID.fromString(userIdHeader)));
    }

    @PostMapping("/{clubId}/approvals/{requestId}/approve")
    public ResponseEntity<ApprovalRequestResponse> approve(@PathVariable UUID clubId,
                                                           @PathVariable UUID requestId,
                                                           @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(governanceService.approve(clubId, requestId, UUID.fromString(userIdHeader)));
    }

    @PostMapping("/{clubId}/approvals/{requestId}/reject")
    public ResponseEntity<ApprovalRequestResponse> reject(@PathVariable UUID clubId,
                                                          @PathVariable UUID requestId,
                                                          @Valid @RequestBody ReasonRequest request,
                                                          @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(governanceService.reject(clubId, requestId, UUID.fromString(userIdHeader), request.reason()));
    }

    @PostMapping("/{clubId}/approvals/{requestId}/withdraw")
    public ResponseEntity<ApprovalRequestResponse> withdraw(@PathVariable UUID clubId,
                                                            @PathVariable UUID requestId,
                                                            @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(governanceService.withdraw(clubId, requestId, UUID.fromString(userIdHeader)));
    }

    @PostMapping("/{clubId}/resignations")
    public ResponseEntity<ApprovalRequestResponse> resign(@PathVariable UUID clubId,
                                                          @Valid @RequestBody(required = false) NoteRequest request,
                                                          @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(governanceService.resign(
                clubId, UUID.fromString(userIdHeader), request != null ? request.note() : null)));
    }

    @PostMapping("/{clubId}/closure-requests")
    public ResponseEntity<ApprovalRequestResponse> requestClosure(@PathVariable UUID clubId,
                                                                  @Valid @RequestBody ReasonRequest request,
                                                                  @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(governanceService.requestClosure(
                clubId, UUID.fromString(userIdHeader), request.reason())));
    }

    @GetMapping("/{clubId}/decision-log")
    public ResponseEntity<List<DecisionLogEntryResponse>> decisionLog(@PathVariable UUID clubId,
                                                                      @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(governanceService.decisionLogOf(clubId, UUID.fromString(userIdHeader)).stream()
                .map(DecisionLogEntryResponse::of)
                .toList());
    }
}
