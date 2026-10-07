package com.educonnect.authservices.controller;

import com.educonnect.authservices.dto.request.AffiliationStatusRequest;
import com.educonnect.authservices.dto.request.ManagedEmailChangeRequest;
import com.educonnect.authservices.dto.request.SuspendAccountRequest;
import com.educonnect.authservices.dto.response.AffiliationStatusView;
import com.educonnect.authservices.models.AccountType;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.authservices.service.AccountStatusService;
import com.educonnect.authservices.service.AdminAuditService;
import com.educonnect.authservices.service.AffiliationLifecycleService;
import com.educonnect.authservices.service.EmailChangeService;
import com.educonnect.common.web.ForbiddenException;
import com.educonnect.common.web.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth/account-management/users")
@PreAuthorize("hasAuthority('PERM_ACCOUNT_MANAGER') or hasRole('ADMIN')")
public class AccountManagementController {

    private static final Set<AccountType> MANAGED = Set.of(AccountType.STUDENT, AccountType.ACADEMICIAN, AccountType.UNKNOWN);

    private final UserRepository userRepository;
    private final AffiliationLifecycleService lifecycleService;
    private final AccountStatusService accountStatusService;
    private final AdminAuditService adminAuditService;
    private final EmailChangeService emailChangeService;

    public AccountManagementController(UserRepository userRepository,
                                       AffiliationLifecycleService lifecycleService,
                                       AccountStatusService accountStatusService,
                                       AdminAuditService adminAuditService,
                                       EmailChangeService emailChangeService) {
        this.userRepository = userRepository;
        this.lifecycleService = lifecycleService;
        this.accountStatusService = accountStatusService;
        this.adminAuditService = adminAuditService;
        this.emailChangeService = emailChangeService;
    }

    @GetMapping
    public ResponseEntity<List<ManagedAccount>> search(@RequestParam String query) {
        String term = query.strip();
        if (term.length() < 3) {
            return ResponseEntity.ok(List.of());
        }
        Map<UUID, User> found = new LinkedHashMap<>();
        userRepository.findFirstByStudentNumber(term).ifPresent(user -> found.put(user.getId(), user));
        userRepository.findTop50ByEmailContainingIgnoreCaseOrderByEmailAsc(term).forEach(user -> found.putIfAbsent(user.getId(), user));
        return ResponseEntity.ok(found.values().stream()
                .filter(user -> MANAGED.contains(AccountType.of(user.getRoles())))
                .map(ManagedAccount::of)
                .toList());
    }

    @GetMapping("/{userId}/affiliations")
    public ResponseEntity<AffiliationStatusView> affiliations(@PathVariable UUID userId) {
        requireManaged(userId);
        return ResponseEntity.ok(lifecycleService.view(userId));
    }

    @PutMapping("/{userId}/student-status")
    public ResponseEntity<AffiliationStatusView> studentStatus(@PathVariable UUID userId,
                                                               @Valid @RequestBody AffiliationStatusRequest request,
                                                               Authentication authentication) {
        requireManaged(userId);
        AffiliationStatusView view = lifecycleService.changeStudentStatus(userId, request, authentication.getName());
        adminAuditService.record("CHANGE_STUDENT_STATUS", "USER", userId, request.status());
        return ResponseEntity.ok(view);
    }

    @PutMapping("/{userId}/staff-status")
    public ResponseEntity<AffiliationStatusView> staffStatus(@PathVariable UUID userId,
                                                             @Valid @RequestBody AffiliationStatusRequest request,
                                                             Authentication authentication) {
        requireManaged(userId);
        AffiliationStatusView view = lifecycleService.changeStaffStatus(userId, request, authentication.getName());
        adminAuditService.record("CHANGE_STAFF_STATUS", "USER", userId, request.status());
        return ResponseEntity.ok(view);
    }

    @PutMapping("/{userId}/suspend")
    public ResponseEntity<String> suspend(@PathVariable UUID userId,
                                          @Valid @RequestBody(required = false) SuspendAccountRequest request,
                                          Authentication authentication) {
        requireManaged(userId);
        String reason = request != null ? request.reason() : null;
        accountStatusService.suspend(userId, authentication.getName(), reason);
        adminAuditService.record("SUSPEND_USER", "USER", userId, reason);
        return ResponseEntity.ok("Kullanıcı hesabı askıya alındı.");
    }

    @PutMapping("/{userId}/reactivate")
    public ResponseEntity<String> reactivate(@PathVariable UUID userId, Authentication authentication) {
        requireManaged(userId);
        accountStatusService.reactivate(userId, authentication.getName());
        adminAuditService.record("REACTIVATE_USER", "USER", userId, null);
        return ResponseEntity.ok("Kullanıcı hesabı yeniden etkinleştirildi.");
    }

    @PutMapping("/{userId}/email")
    public ResponseEntity<ManagedAccount> changeEmail(@PathVariable UUID userId,
                                                      @Valid @RequestBody ManagedEmailChangeRequest request) {
        requireManaged(userId);
        User user = emailChangeService.change(userId, request.newEmail());
        adminAuditService.record("CHANGE_EMAIL", "USER", userId, request.reason());
        return ResponseEntity.ok(ManagedAccount.of(user));
    }

    private void requireManaged(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Kullanıcı bulunamadı."));
        if (!MANAGED.contains(AccountType.of(user.getRoles()))) {
            throw new ForbiddenException("TARGET_NOT_MANAGED", "Admin ve görevli hesapları buradan yönetilmez.");
        }
    }

    public record ManagedAccount(UUID id, String email, Set<String> roles, String status, String studentNumber,
                                 String studentStatus, String staffStatus, Instant closureDueAt) {

        static ManagedAccount of(User user) {
            return new ManagedAccount(user.getId(), user.getEmail(),
                    user.getRoles().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new)),
                    user.getStatus().name(), user.getStudentNumber(),
                    user.getStudentStatus() == null ? null : user.getStudentStatus().name(),
                    user.getStaffStatus() == null ? null : user.getStaffStatus().name(), user.getClosureDueAt());
        }
    }
}
