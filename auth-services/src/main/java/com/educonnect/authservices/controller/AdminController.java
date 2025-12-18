package com.educonnect.authservices.controller;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.models.AcademicianRegistrationRequest;
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
@RequestMapping("/api/auth/admin")
public class AdminController {

    @Autowired
    private AuthServiceImpl authService; // Veya ayrı bir AdminService

    @Autowired
    private UserRepository userRepository;



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
        try {
            System.out.println("DEBUG: /pending/club-official endpoint'ine istek geldi");
            List<User> users = userRepository.findAllByRolesContaining(Role.ROLE_PENDING_CLUB_OFFICIAL);
            System.out.println("DEBUG: Bulunan kullanıcı sayısı: " + users.size());

            List<Map<String, Object>> result = users.stream()
                    .map(u -> {
                        try {
                            return Map.of(
                                    "id", u.getId(),
                                    "email", u.getEmail(),
                                    "roles", u.getRoles().stream()
                                            .map(Role::name)
                                            .collect(Collectors.toSet())
                            );
                        } catch (Exception e) {
                            System.err.println("Kullanıcı map'leme hatası: " + e.getMessage());
                            e.printStackTrace();
                            throw e;
                        }
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("🔥 /pending/club-official endpoint hatası:");
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(null);
        }
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

    // --- AKADEMİSYEN İŞLEMLERİ ---

    @GetMapping("/requests/academicians")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAcademicianRequests() {
        try {
            System.out.println("DEBUG: Controller'a girildi. Servis çağrılıyor...");
            var result = authService.getAllAcademicianRequests();
            System.out.println("DEBUG: Servisten veri geldi. Boyut: " + (result != null ? result.size() : "null"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("🔥🔥🔥 BEKLENMEYEN HATA DETAYI 🔥🔥🔥");
            e.printStackTrace(); // <--- BU SATIR HATAYI GÖSTERİR
            return ResponseEntity.internalServerError().body("Sunucu Hatası: " + e.getMessage());
        }
    }

    // 2. Onayla
    @PostMapping("/approve-academician/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> approveAcademician(@PathVariable UUID userId) {
        authService.approveAcademician(userId);
        return ResponseEntity.ok("Akademisyen onaylandı.");
    }

    // 3. Reddet
    @PostMapping("/reject-academician/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> rejectAcademician(@PathVariable UUID userId) {
        authService.rejectAcademician(userId);
        return ResponseEntity.ok("Akademisyen başvurusu reddedildi.");
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
        authService.deleteUser(userId);
        return ResponseEntity.ok("Kullanıcı başarıyla silindi.");
    }
}
