package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.AnnouncementRequest;
import com.educonnect.clubservice.dto.response.AnnouncementResponse;
import com.educonnect.clubservice.dto.response.ApprovalRequestResponse;
import com.educonnect.clubservice.dto.response.PageResponse;
import com.educonnect.clubservice.service.ClubAnnouncementService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/clubs/{clubId}/announcements")
@PreAuthorize("isAuthenticated()")
public class ClubAnnouncementController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubAnnouncementService announcementService;
    private final ClubGovernanceService governanceService;

    public ClubAnnouncementController(ClubAnnouncementService announcementService, ClubGovernanceService governanceService) {
        this.announcementService = announcementService;
        this.governanceService = governanceService;
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApprovalRequestResponse> submit(@PathVariable UUID clubId,
                                                          @Valid @RequestBody AnnouncementRequest request,
                                                          @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(announcementService.submit(
                clubId, UUID.fromString(userIdHeader), request.title(), request.body())));
    }

    @GetMapping
    public ResponseEntity<PageResponse<AnnouncementResponse>> list(@PathVariable UUID clubId,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(required = false) Integer size,
                                                                  @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(announcementService.announcementsOf(clubId, UUID.fromString(userIdHeader), page, size));
    }

    @DeleteMapping("/{announcementId}")
    public ResponseEntity<AnnouncementResponse> remove(@PathVariable UUID clubId,
                                                       @PathVariable UUID announcementId,
                                                       @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(announcementService.remove(clubId, announcementId, UUID.fromString(userIdHeader)));
    }
}
