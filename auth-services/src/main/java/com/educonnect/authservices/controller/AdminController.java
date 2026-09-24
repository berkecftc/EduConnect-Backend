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
    public ResponseEntity<String> rejectAcademician(
            @PathVariable UUID userId,
            @RequestParam(value = "reason", required = false) String reason) {
        authService.rejectAcademician(userId, reason);
        return ResponseEntity.ok("Akademisyen başvurusu reddedildi.");
    }

    // --- ÖĞRENCİ İŞLEMLERİ ---

    // 1. Bekleyen öğrenci başvurularını listele
    @GetMapping("/requests/students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getStudentRequests() {
        try {
            System.out.println("DEBUG: /requests/students endpoint'ine istek geldi");
            var result = authService.getAllStudentRequests();
            System.out.println("DEBUG: Servisten veri geldi. Boyut: " + (result != null ? result.size() : "null"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("🔥🔥🔥 BEKLENMEYEN HATA DETAYI 🔥🔥🔥");
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Sunucu Hatası: " + e.getMessage());
        }
    }

    // 2. Öğrenci başvurusunu onayla (requestId ile)
    @PostMapping("/approve-student/{requestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> approveStudent(@PathVariable Long requestId) {
        authService.approveStudent(requestId);
        return ResponseEntity.ok("Öğrenci onaylandı.");
    }

    // 3. Öğrenci başvurusunu reddet (requestId ile)
    @PostMapping("/reject-student/{requestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> rejectStudent(
            @PathVariable Long requestId,
            @RequestParam(value = "reason", required = false) String reason) {
        authService.rejectStudent(requestId, reason);
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
        authService.deleteUser(userId);
        return ResponseEntity.ok("Kullanıcı başarıyla silindi.");
    }
}
