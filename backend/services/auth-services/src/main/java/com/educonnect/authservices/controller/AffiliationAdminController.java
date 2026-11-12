package com.educonnect.authservices.controller;

import com.educonnect.authservices.dto.request.AffiliationStatusRequest;
import com.educonnect.authservices.dto.response.AffiliationStatusView;
import com.educonnect.authservices.service.AdminAuditService;
import com.educonnect.authservices.service.AffiliationLifecycleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth/admin/users/{userId}")
public class AffiliationAdminController {

    private final AffiliationLifecycleService lifecycleService;
    private final AdminAuditService adminAuditService;

    public AffiliationAdminController(AffiliationLifecycleService lifecycleService, AdminAuditService adminAuditService) {
        this.lifecycleService = lifecycleService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping("/affiliations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AffiliationStatusView> view(@PathVariable UUID userId) {
        return ResponseEntity.ok(lifecycleService.view(userId));
    }

    @PutMapping("/student-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AffiliationStatusView> studentStatus(@PathVariable UUID userId,
                                                               @Valid @RequestBody AffiliationStatusRequest request,
                                                               Authentication authentication) {
        AffiliationStatusView view = lifecycleService.changeStudentStatus(userId, request, authentication.getName());
        adminAuditService.record("CHANGE_STUDENT_STATUS", "USER", userId, request.status());
        return ResponseEntity.ok(view);
    }

    @PutMapping("/staff-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AffiliationStatusView> staffStatus(@PathVariable UUID userId,
                                                             @Valid @RequestBody AffiliationStatusRequest request,
                                                             Authentication authentication) {
        AffiliationStatusView view = lifecycleService.changeStaffStatus(userId, request, authentication.getName());
        adminAuditService.record("CHANGE_STAFF_STATUS", "USER", userId, request.status());
        return ResponseEntity.ok(view);
    }
}
