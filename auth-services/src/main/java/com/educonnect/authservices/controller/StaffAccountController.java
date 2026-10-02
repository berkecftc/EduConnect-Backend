package com.educonnect.authservices.controller;

import com.educonnect.authservices.dto.request.StaffAccountRequest;
import com.educonnect.authservices.dto.request.StaffGrantsRequest;
import com.educonnect.authservices.dto.response.StaffAccountView;
import com.educonnect.authservices.service.AdminAuditService;
import com.educonnect.authservices.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class StaffAccountController {

    private final StaffAccountService staffAccountService;
    private final AdminAuditService adminAuditService;

    public StaffAccountController(StaffAccountService staffAccountService, AdminAuditService adminAuditService) {
        this.staffAccountService = staffAccountService;
        this.adminAuditService = adminAuditService;
    }

    @PostMapping("/api/auth/admin/staff-accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffAccountView> create(@Valid @RequestBody StaffAccountRequest request) {
        StaffAccountView view = staffAccountService.create(request);
        adminAuditService.record("CREATE_STAFF_ACCOUNT", "USER", view.id(), permissions(view));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/api/auth/admin/staff-accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StaffAccountView>> list() {
        return ResponseEntity.ok(staffAccountService.list());
    }

    @PutMapping("/api/auth/admin/staff-accounts/{userId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffAccountView> replaceGrants(@PathVariable UUID userId, @Valid @RequestBody StaffGrantsRequest request) {
        StaffAccountView view = staffAccountService.replaceGrants(userId, request.grants());
        adminAuditService.record("CHANGE_STAFF_PERMISSIONS", "USER", userId, permissions(view));
        return ResponseEntity.ok(view);
    }

    @GetMapping("/api/auth/internal/staff/{userId}/grants")
    public ResponseEntity<List<StaffAccountView.Grant>> grants(@PathVariable UUID userId) {
        return ResponseEntity.ok(staffAccountService.grantsOf(userId));
    }

    private static String permissions(StaffAccountView view) {
        return view.grants().stream()
                .map(grant -> grant.facultyId() == null ? grant.permission().name() : grant.permission() + ":" + grant.facultyId())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}
