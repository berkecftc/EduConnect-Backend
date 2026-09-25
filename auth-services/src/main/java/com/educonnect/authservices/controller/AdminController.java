package com.educonnect.authservices.controller;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.models.AcademicianRegistrationRequest;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.dto.request.SuspendAccountRequest;
import com.educonnect.authservices.service.AccountStatusService;
import com.educonnect.authservices.service.AcademicianAssignmentGuard;
import com.educonnect.authservices.service.AdminAuditService;
import com.educonnect.authservices.dto.response.AdminAuditPage;
import com.educonnect.authservices.service.AuthServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth/admin")
public class AdminController {

    @Autowired
    private AuthServiceImpl authService; // Veya ayrı bir AdminService

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountStatusService accountStatusService;

    @Autowired
    private AdminAuditService adminAuditService;

    @Autowired
    private AcademicianAssignmentGuard academicianAssignmentGuard;



    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping({"/promote/{userId}", "/revoke/{userId}"})
    public ResponseEntity<String> changeAdminRole(@PathVariable UUID userId) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body("Admin rolü mevcut hesaplara verilip alınamaz. Admin hesapları ayrı platform hesaplarıdır ve yalnız ilk kurulumda (educonnect.auth.bootstrap-admin) açılır.");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @RequestMapping(value = {"/pending/club-official", "/approve/club-official/{userId}", "/reject/club-official/{userId}"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> clubOfficialRequests() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body("Genel kulüp yetkilisi başvurusu kapatıldı. Kulüp görevleri kulüp kuruluş başvurusu ve danışman onaylı görev atamasıyla verilir.");
    }

    // --- AKADEMİSYEN İŞLEMLERİ ---

    @GetMapping("/requests/academicians")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAcademicianRequests() {
        return ResponseEntity.ok(authService.getAllAcademicianRequests());
    }

    // 2. Onayla
    @PostMapping("/approve-academician/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> approveAcademician(@PathVariable UUID userId) {
        authService.approveAcademician(userId);
        adminAuditService.record("APPROVE_ACADEMICIAN", "USER", userId, null);
        return ResponseEntity.ok("Akademisyen onaylandı.");
    }

    // 3. Reddet
    @PostMapping("/reject-academician/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> rejectAcademician(
            @PathVariable UUID userId,
            @RequestParam(value = "reason", required = false) String reason) {
        authService.rejectAcademician(userId, reason);
        adminAuditService.record("REJECT_ACADEMICIAN", "USER", userId, reason);
        return ResponseEntity.ok("Akademisyen başvurusu reddedildi.");
    }

    // --- ÖĞRENCİ İŞLEMLERİ ---

    // 1. Bekleyen öğrenci başvurularını listele
    @GetMapping("/requests/students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getStudentRequests() {
        return ResponseEntity.ok(authService.getAllStudentRequests());
    }

    // 2. Öğrenci başvurusunu onayla (requestId ile)
    @PostMapping("/approve-student/{requestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> approveStudent(@PathVariable Long requestId) {
        authService.approveStudent(requestId);
        adminAuditService.record("APPROVE_STUDENT", "STUDENT_REQUEST", requestId, null);
        return ResponseEntity.ok("Öğrenci onaylandı.");
    }

    // 3. Öğrenci başvurusunu reddet (requestId ile)
    @PostMapping("/reject-student/{requestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> rejectStudent(
            @PathVariable Long requestId,
            @RequestParam(value = "reason", required = false) String reason) {
        authService.rejectStudent(requestId, reason);
        adminAuditService.record("REJECT_STUDENT", "STUDENT_REQUEST", requestId, reason);
        return ResponseEntity.ok("Öğrenci başvurusu reddedildi.");
    }

    // --- KULLANICI YÖNETİMİ ---

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteUser(@PathVariable UUID userId) {
        academicianAssignmentGuard.requireNoActiveAssignments(userId);
        authService.deleteUser(userId);
        adminAuditService.record("DELETE_USER", "USER", userId, null);
        return ResponseEntity.ok("Kullanıcı başarıyla silindi.");
    }

    @PutMapping("/users/{userId}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> suspendUser(@PathVariable UUID userId,
                                              @RequestBody(required = false) SuspendAccountRequest request,
                                              Authentication authentication) {
        accountStatusService.suspend(userId, authentication.getName(), request != null ? request.reason() : null);
        adminAuditService.record("SUSPEND_USER", "USER", userId, request != null ? request.reason() : null);
        return ResponseEntity.ok("Kullanıcı hesabı askıya alındı.");
    }

    @PutMapping("/users/{userId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> reactivateUser(@PathVariable UUID userId, Authentication authentication) {
        accountStatusService.reactivate(userId, authentication.getName());
        adminAuditService.record("REACTIVATE_USER", "USER", userId, null);
        return ResponseEntity.ok("Kullanıcı hesabı yeniden etkinleştirildi.");
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminAuditPage> getAuditLog(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(adminAuditService.list(page, size));
    }
}
