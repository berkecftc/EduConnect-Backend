package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.ProfileChangeRequest;
import com.educonnect.clubservice.dto.response.ApprovalRequestResponse;
import com.educonnect.clubservice.service.ClubGovernanceService;
import com.educonnect.clubservice.service.ClubProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/clubs")
@PreAuthorize("hasRole('STUDENT')")
public class ClubProfileController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubProfileService profileService;
    private final ClubGovernanceService governanceService;

    public ClubProfileController(ClubProfileService profileService, ClubGovernanceService governanceService) {
        this.profileService = profileService;
        this.governanceService = governanceService;
    }

    @PostMapping("/{clubId}/profile-change-requests")
    public ResponseEntity<ApprovalRequestResponse> requestProfileChange(@PathVariable UUID clubId,
                                                                        @Valid @RequestBody ProfileChangeRequest request,
                                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(
                profileService.requestProfileChange(clubId, UUID.fromString(userIdHeader), request)));
    }

    @PostMapping(value = "/{clubId}/logo-change-requests", consumes = "multipart/form-data")
    public ResponseEntity<ApprovalRequestResponse> requestLogoChange(@PathVariable UUID clubId,
                                                                     @RequestParam("file") MultipartFile file,
                                                                     @RequestParam(value = "note", required = false) @Size(max = 1000) String note,
                                                                     @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(
                profileService.requestLogoChange(clubId, UUID.fromString(userIdHeader), file, note)));
    }
}
