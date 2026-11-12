package com.educonnect.authservices.controller;

import com.educonnect.authservices.dto.response.AcademicianRequestAdminView;
import com.educonnect.authservices.dto.response.StudentRequestAdminView;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.service.AdminAuditService;
import com.educonnect.authservices.service.RegistrationApprovalService;
import com.educonnect.authservices.service.VerificationScope;
import com.educonnect.common.web.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/verification")
public class VerificationController {

    private final RegistrationApprovalService approvalService;
    private final StudentRequestRepository studentRequestRepository;
    private final VerificationScope verificationScope;
    private final AdminAuditService adminAuditService;

    public VerificationController(RegistrationApprovalService approvalService,
                                  StudentRequestRepository studentRequestRepository,
                                  VerificationScope verificationScope,
                                  AdminAuditService adminAuditService) {
        this.approvalService = approvalService;
        this.studentRequestRepository = studentRequestRepository;
        this.verificationScope = verificationScope;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping("/students")
    @PreAuthorize("hasAuthority('PERM_STUDENT_VERIFIER') or hasRole('ADMIN')")
    public ResponseEntity<List<StudentRequestAdminView>> studentRequests(Authentication authentication) {
        return ResponseEntity.ok(approvalService.getStudentRequests(verificationScope.studentRequests(authentication.getName())));
    }

    @PostMapping("/students/{requestId}/approve")
    @PreAuthorize("hasAuthority('PERM_STUDENT_VERIFIER') or hasRole('ADMIN')")
    public ResponseEntity<String> approveStudent(@PathVariable Long requestId, Authentication authentication) {
        verificationScope.requireStudentRequest(authentication.getName(), studentRequest(requestId));
        approvalService.approveStudent(requestId);
        adminAuditService.record("APPROVE_STUDENT", "STUDENT_REQUEST", requestId, null);
        return ResponseEntity.ok("Öğrenci onaylandı.");
    }

    @PostMapping("/students/{requestId}/reject")
    @PreAuthorize("hasAuthority('PERM_STUDENT_VERIFIER') or hasRole('ADMIN')")
    public ResponseEntity<String> rejectStudent(@PathVariable Long requestId,
                                                @RequestParam(value = "reason", required = false) String reason,
                                                Authentication authentication) {
        verificationScope.requireStudentRequest(authentication.getName(), studentRequest(requestId));
        approvalService.rejectStudent(requestId, reason);
        adminAuditService.record("REJECT_STUDENT", "STUDENT_REQUEST", requestId, reason);
        return ResponseEntity.ok("Öğrenci başvurusu reddedildi.");
    }

    @GetMapping("/academicians")
    @PreAuthorize("hasAuthority('PERM_STAFF_VERIFIER') or hasRole('ADMIN')")
    public ResponseEntity<List<AcademicianRequestAdminView>> academicianRequests() {
        return ResponseEntity.ok(approvalService.getAllAcademicianRequests());
    }

    @PostMapping("/academicians/{userId}/approve")
    @PreAuthorize("hasAuthority('PERM_STAFF_VERIFIER') or hasRole('ADMIN')")
    public ResponseEntity<String> approveAcademician(@PathVariable UUID userId) {
        approvalService.approveAcademician(userId);
        adminAuditService.record("APPROVE_ACADEMICIAN", "USER", userId, null);
        return ResponseEntity.ok("Akademisyen onaylandı.");
    }

    @PostMapping("/academicians/{userId}/reject")
    @PreAuthorize("hasAuthority('PERM_STAFF_VERIFIER') or hasRole('ADMIN')")
    public ResponseEntity<String> rejectAcademician(@PathVariable UUID userId,
                                                    @RequestParam(value = "reason", required = false) String reason) {
        approvalService.rejectAcademician(userId, reason);
        adminAuditService.record("REJECT_ACADEMICIAN", "USER", userId, reason);
        return ResponseEntity.ok("Akademisyen başvurusu reddedildi.");
    }

    private StudentRegistrationRequest studentRequest(Long requestId) {
        return studentRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("STUDENT_REQUEST_NOT_FOUND", "Öğrenci başvurusu bulunamadı."));
    }
}
