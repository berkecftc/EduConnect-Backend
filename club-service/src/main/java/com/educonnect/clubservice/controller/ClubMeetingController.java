package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.MeetingRequest;
import com.educonnect.clubservice.dto.response.ApprovalRequestResponse;
import com.educonnect.clubservice.dto.response.DecisionBookEntryResponse;
import com.educonnect.clubservice.dto.response.MeetingResponse;
import com.educonnect.clubservice.service.ClubGovernanceService;
import com.educonnect.clubservice.service.ClubMeetingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs/{clubId}")
@PreAuthorize("isAuthenticated()")
public class ClubMeetingController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubMeetingService meetingService;
    private final ClubGovernanceService governanceService;

    public ClubMeetingController(ClubMeetingService meetingService, ClubGovernanceService governanceService) {
        this.meetingService = meetingService;
        this.governanceService = governanceService;
    }

    @PostMapping("/meetings")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApprovalRequestResponse> submit(@PathVariable UUID clubId,
                                                          @Valid @RequestBody MeetingRequest request,
                                                          @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(
                meetingService.submit(clubId, UUID.fromString(userIdHeader), request)));
    }

    @GetMapping("/meetings")
    public ResponseEntity<List<MeetingResponse>> meetings(@PathVariable UUID clubId,
                                                          @RequestParam(required = false) Integer academicYear,
                                                          @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(meetingService.meetingsOf(clubId, UUID.fromString(userIdHeader), academicYear));
    }

    @GetMapping("/decision-book")
    public ResponseEntity<List<DecisionBookEntryResponse>> decisionBook(@PathVariable UUID clubId,
                                                                        @RequestParam(required = false) Integer academicYear,
                                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(meetingService.decisionBookOf(clubId, UUID.fromString(userIdHeader), academicYear));
    }
}
