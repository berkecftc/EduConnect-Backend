package com.educonnect.userservice.controller;

import com.educonnect.common.security.IdentityHeaders;
import com.educonnect.userservice.dto.request.ProfileChangeRequestDto;
import com.educonnect.userservice.dto.response.ProfileChangeResponse;
import com.educonnect.userservice.models.ProfileChangeRequest;
import com.educonnect.userservice.service.ProfileChangeService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class ProfileChangeController {

    private final ProfileChangeService changeService;

    public ProfileChangeController(ProfileChangeService changeService) {
        this.changeService = changeService;
    }

    @PostMapping("/profile/me/change-requests")
    public ResponseEntity<ProfileChangeResponse> submit(@Valid @RequestBody ProfileChangeRequestDto request,
                                                        @RequestHeader(IdentityHeaders.USER_ID) UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(changeService.submit(userId, request));
    }

    @GetMapping("/profile/me/change-requests")
    public ResponseEntity<List<ProfileChangeResponse>> mine(@RequestHeader(IdentityHeaders.USER_ID) UUID userId) {
        return ResponseEntity.ok(changeService.mine(userId));
    }

    @GetMapping("/verification/change-requests")
    @PreAuthorize("hasAuthority('PERM_STUDENT_VERIFIER') or hasAuthority('PERM_STAFF_VERIFIER')")
    public ResponseEntity<List<ProfileChangeResponse>> verifierList(
            @RequestParam(defaultValue = "PENDING") ProfileChangeRequest.Status status,
            @RequestHeader(IdentityHeaders.USER_ID) UUID verifierId) {
        return ResponseEntity.ok(changeService.listForVerifier(verifierId, status));
    }

    @PostMapping("/verification/change-requests/{requestId}/approve")
    @PreAuthorize("hasAuthority('PERM_STUDENT_VERIFIER') or hasAuthority('PERM_STAFF_VERIFIER')")
    public ResponseEntity<ProfileChangeResponse> verifierApprove(@PathVariable UUID requestId,
                                                                 @RequestHeader(IdentityHeaders.USER_ID) UUID verifierId) {
        return ResponseEntity.ok(changeService.approveAsVerifier(requestId, verifierId));
    }

    @PostMapping("/verification/change-requests/{requestId}/reject")
    @PreAuthorize("hasAuthority('PERM_STUDENT_VERIFIER') or hasAuthority('PERM_STAFF_VERIFIER')")
    public ResponseEntity<ProfileChangeResponse> verifierReject(@PathVariable UUID requestId,
                                                                @RequestBody(required = false) Map<String, String> body,
                                                                @RequestHeader(IdentityHeaders.USER_ID) UUID verifierId) {
        return ResponseEntity.ok(changeService.rejectAsVerifier(requestId, verifierId, body == null ? null : body.get("note")));
    }

    @GetMapping("/admin/change-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProfileChangeResponse>> list(
            @RequestParam(defaultValue = "PENDING") ProfileChangeRequest.Status status) {
        return ResponseEntity.ok(changeService.list(status));
    }

    @PostMapping("/admin/change-requests/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProfileChangeResponse> approve(@PathVariable UUID requestId,
                                                         @RequestHeader(IdentityHeaders.USER_ID) UUID adminId) {
        return ResponseEntity.ok(changeService.approve(requestId, adminId));
    }

    @PostMapping("/admin/change-requests/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProfileChangeResponse> reject(@PathVariable UUID requestId,
                                                        @RequestBody(required = false) Map<String, String> body,
                                                        @RequestHeader(IdentityHeaders.USER_ID) UUID adminId) {
        return ResponseEntity.ok(changeService.reject(requestId, adminId, body == null ? null : body.get("note")));
    }
}
