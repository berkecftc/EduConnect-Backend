package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.AdvisorChangeProposal;
import com.educonnect.clubservice.dto.request.AppointPresidentRequest;
import com.educonnect.clubservice.dto.request.ReasonRequest;
import com.educonnect.clubservice.dto.request.RejectionReasonRequest;
import com.educonnect.clubservice.dto.response.AdvisorChangeRequestResponse;
import com.educonnect.clubservice.dto.response.ClubResponse;
import com.educonnect.clubservice.dto.response.MemberDTO;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.service.AdvisorChangeService;
import com.educonnect.clubservice.service.ClubClosureService;
import com.educonnect.clubservice.service.ClubLeadershipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ClubContinuityController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubLeadershipService leadershipService;
    private final AdvisorChangeService advisorChangeService;
    private final ClubClosureService closureService;

    public ClubContinuityController(ClubLeadershipService leadershipService,
                                    AdvisorChangeService advisorChangeService,
                                    ClubClosureService closureService) {
        this.leadershipService = leadershipService;
        this.advisorChangeService = advisorChangeService;
        this.closureService = closureService;
    }

    @PutMapping("/api/academician/clubs/{clubId}/president")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<MemberDTO> appointPresident(@PathVariable UUID clubId,
                                                      @Valid @RequestBody AppointPresidentRequest request,
                                                      @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        ClubMembership president = leadershipService.appointPresident(clubId, UUID.fromString(userIdHeader), request.studentId());
        MemberDTO body = new MemberDTO(president.getStudentId(), president.getClubRole());
        body.setActive(president.isActive());
        body.setTermStartDate(president.getTermStartDate());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/api/academician/clubs/{clubId}/close")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<ClubResponse> closeClub(@PathVariable UUID clubId,
                                                  @Valid @RequestBody ReasonRequest request,
                                                  @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(ClubResponse.from(
                closureService.closeByAdvisor(clubId, UUID.fromString(userIdHeader), request.reason())));
    }

    @PostMapping("/api/academician/clubs/{clubId}/advisor/resign")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<Void> resignAsAdvisor(@PathVariable UUID clubId,
                                                @Valid @RequestBody(required = false) RejectionReasonRequest reason,
                                                @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        advisorChangeService.resign(clubId, UUID.fromString(userIdHeader), reason != null ? reason.rejectionReason() : null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/academician/advisor-change-requests")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<List<AdvisorChangeRequestResponse>> pendingAdvisorOffers(
            @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(advisorChangeService.getPendingForAdvisor(UUID.fromString(userIdHeader)));
    }

    @PutMapping("/api/academician/advisor-change-requests/{requestId}/accept")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<AdvisorChangeRequestResponse> acceptAdvisorOffer(@PathVariable UUID requestId,
                                                                           @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(advisorChangeService.accept(requestId, UUID.fromString(userIdHeader)));
    }

    @PutMapping("/api/academician/advisor-change-requests/{requestId}/reject")
    @PreAuthorize("hasRole('ACADEMICIAN')")
    public ResponseEntity<AdvisorChangeRequestResponse> rejectAdvisorOffer(
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) RejectionReasonRequest reason,
            @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(advisorChangeService.reject(requestId, UUID.fromString(userIdHeader),
                reason != null ? reason.rejectionReason() : null));
    }

    @PostMapping("/api/clubs/{clubId}/advisor-change-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdvisorChangeRequestResponse> proposeAdvisor(@PathVariable UUID clubId,
                                                                       @Valid @RequestBody AdvisorChangeProposal proposal,
                                                                       @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(advisorChangeService.propose(clubId, UUID.fromString(userIdHeader), proposal));
    }

    @GetMapping("/api/clubs/{clubId}/advisor-change-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AdvisorChangeRequestResponse>> advisorChangeRequests(@PathVariable UUID clubId,
                                                                                   @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(advisorChangeService.getClubRequests(clubId, UUID.fromString(userIdHeader)));
    }

    @DeleteMapping("/api/clubs/{clubId}/advisor-change-requests/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelAdvisorChange(@PathVariable UUID clubId,
                                                    @PathVariable UUID requestId,
                                                    @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        advisorChangeService.cancel(clubId, requestId, UUID.fromString(userIdHeader));
        return ResponseEntity.noContent().build();
    }
}
