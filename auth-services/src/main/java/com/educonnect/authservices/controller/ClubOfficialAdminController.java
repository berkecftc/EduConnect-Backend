package com.educonnect.authservices.controller;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.service.AdminAuditService;
import com.educonnect.authservices.service.AuthServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth/admin")
@ConditionalOnProperty(name = "educonnect.club.admin-access.enabled", havingValue = "true", matchIfMissing = true)
public class ClubOfficialAdminController {

    private final AuthServiceImpl authService;
    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    public ClubOfficialAdminController(AuthServiceImpl authService, UserRepository userRepository,
                                       AdminAuditService adminAuditService) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.adminAuditService = adminAuditService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/approve/club-official/{userId}")
    public ResponseEntity<String> approveClubOfficial(@PathVariable UUID userId) {
        authService.approveClubOfficial(userId);
        adminAuditService.record("APPROVE_CLUB_OFFICIAL", "USER", userId, null);
        return ResponseEntity.ok("Club official request approved.");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reject/club-official/{userId}")
    public ResponseEntity<String> rejectClubOfficial(@PathVariable UUID userId) {
        authService.rejectClubOfficial(userId);
        adminAuditService.record("REJECT_CLUB_OFFICIAL", "USER", userId, null);
        return ResponseEntity.ok("Club official request rejected.");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending/club-official")
    public ResponseEntity<List<Map<String, Object>>> listPendingClubOfficialRequests() {
        List<Map<String, Object>> result = userRepository.findAllByRolesContaining(Role.ROLE_PENDING_CLUB_OFFICIAL).stream()
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "roles", user.getRoles().stream().map(Role::name).collect(Collectors.toSet())))
                .toList();
        return ResponseEntity.ok(result);
    }
}
