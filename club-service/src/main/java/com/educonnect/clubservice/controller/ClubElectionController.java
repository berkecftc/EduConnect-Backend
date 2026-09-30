package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.CandidacyRequest;
import com.educonnect.clubservice.dto.request.OpenElectionRequest;
import com.educonnect.clubservice.dto.request.VoteRequest;
import com.educonnect.clubservice.dto.response.ApprovalRequestResponse;
import com.educonnect.clubservice.dto.response.ElectionResponse;
import com.educonnect.clubservice.service.ClubElectionService;
import com.educonnect.clubservice.service.ClubGovernanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/clubs/{clubId}/elections")
@PreAuthorize("isAuthenticated()")
public class ClubElectionController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubElectionService electionService;
    private final ClubGovernanceService governanceService;

    public ClubElectionController(ClubElectionService electionService, ClubGovernanceService governanceService) {
        this.electionService = electionService;
        this.governanceService = governanceService;
    }

    @GetMapping
    public ResponseEntity<List<ElectionResponse>> elections(@PathVariable UUID clubId,
                                                            @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(electionService.electionsOf(clubId, UUID.fromString(userIdHeader)));
    }

    @GetMapping("/{electionId}")
    public ResponseEntity<ElectionResponse> election(@PathVariable UUID clubId,
                                                     @PathVariable UUID electionId,
                                                     @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(electionService.electionOf(clubId, electionId, UUID.fromString(userIdHeader)));
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ElectionResponse> open(@PathVariable UUID clubId,
                                                 @Valid @RequestBody OpenElectionRequest request,
                                                 @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(electionService.open(clubId, UUID.fromString(userIdHeader), request));
    }

    @PostMapping("/{electionId}/candidacy")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ElectionResponse> nominate(@PathVariable UUID clubId,
                                                     @PathVariable UUID electionId,
                                                     @Valid @RequestBody CandidacyRequest request,
                                                     @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(electionService.nominate(clubId, electionId, UUID.fromString(userIdHeader), request.ballot()));
    }

    @DeleteMapping("/{electionId}/candidacy")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ElectionResponse> withdraw(@PathVariable UUID clubId,
                                                     @PathVariable UUID electionId,
                                                     @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(electionService.withdraw(clubId, electionId, UUID.fromString(userIdHeader)));
    }

    @PostMapping("/{electionId}/start-voting")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ElectionResponse> startVoting(@PathVariable UUID clubId,
                                                        @PathVariable UUID electionId,
                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(electionService.startVoting(clubId, electionId, UUID.fromString(userIdHeader)));
    }

    @PostMapping("/{electionId}/votes")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ElectionResponse> vote(@PathVariable UUID clubId,
                                                 @PathVariable UUID electionId,
                                                 @Valid @RequestBody VoteRequest request,
                                                 @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(electionService.vote(clubId, electionId, UUID.fromString(userIdHeader), request.ballot(),
                request.candidateIds()));
    }

    @PostMapping("/{electionId}/close")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApprovalRequestResponse> close(@PathVariable UUID clubId,
                                                         @PathVariable UUID electionId,
                                                         @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(governanceService.toResponse(electionService.close(clubId, electionId,
                UUID.fromString(userIdHeader))));
    }

    @PostMapping("/{electionId}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ElectionResponse> cancel(@PathVariable UUID clubId,
                                                   @PathVariable UUID electionId,
                                                   @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(electionService.cancel(clubId, electionId, UUID.fromString(userIdHeader)));
    }
}
