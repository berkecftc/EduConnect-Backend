package com.educonnect.authservices.controller;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.service.AuthServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AuthServiceImpl authService; // Veya ayrı bir AdminService

    @Autowired
    private UserRepository userRepository;

    // Sadece ROLE_ADMIN erişsin
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/approve/academician/{userId}")
    public ResponseEntity<String> approveAcademician(@PathVariable UUID userId) {
        authService.approveAcademician(userId);
        return ResponseEntity.ok("Academician approved and profile creation initiated.");
    }

    // Kulüp görevlisi talebini onayla
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/approve/club-official/{userId}")
    public ResponseEntity<String> approveClubOfficial(@PathVariable UUID userId) {
        authService.approveClubOfficial(userId);
        return ResponseEntity.ok("Club official request approved.");
    }

    // Kulüp görevlisi talebini reddet
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reject/club-official/{userId}")
    public ResponseEntity<String> rejectClubOfficial(@PathVariable UUID userId) {
        authService.rejectClubOfficial(userId);
        return ResponseEntity.ok("Club official request rejected.");
    }

    // Bekleyen kulüp görevlisi taleplerini listele
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending/club-official")
    public ResponseEntity<List<Map<String, Object>>> listPendingClubOfficialRequests() {
        List<User> users = userRepository.findAllByRolesContaining(Role.ROLE_PENDING_CLUB_OFFICIAL);
        List<Map<String, Object>> result = users.stream()
                .map(u -> Map.of(
                        "id", u.getId(),
                        "email", u.getEmail(),
                        "roles", u.getRoles()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // Kullanıcıyı admin yap
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/promote/{userId}")
    public ResponseEntity<String> promoteToAdmin(@PathVariable UUID userId) {
        authService.promoteToAdmin(userId);
        return ResponseEntity.ok("User promoted to ROLE_ADMIN.");
    }

    // Kullanıcıdan admin rolünü al
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/revoke/{userId}")
    public ResponseEntity<String> revokeAdmin(@PathVariable UUID userId) {
        authService.revokeAdmin(userId);
        return ResponseEntity.ok("User admin role revoked.");
    }
}
