package com.educonnect.authservices.controller;

import com.educonnect.authservices.dto.request.ChangePasswordRequest;
import com.educonnect.authservices.dto.request.LoginRequest;
import com.educonnect.authservices.dto.request.RegisterRequest;
import com.educonnect.authservices.dto.response.AuthResponse;
import com.educonnect.authservices.service.AuthServiceImpl;
import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthServiceImpl authService;
    private final UserRepository userRepository;

    @Autowired
    public AuthController(AuthServiceImpl authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request
    ) {
        // TODO: Gelen request'i doğrula (Validation)
        return ResponseEntity.ok(authService.register(request));
    }

    // Student kaydı için özel endpoint
    @PostMapping("/register/student")
    public ResponseEntity<AuthResponse> registerStudent(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.registerStudent(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request
    ) {

        return ResponseEntity.ok(authService.login(request));
    }

    // --- YENİ ENDPOINT: Akademisyen Başvurusu ---
    @PostMapping("/request/academician-account")
    public ResponseEntity<String> requestAcademicianAccount(
            @RequestBody RegisterRequest request
    ) {
        // Servis katmanında bu isteği işleyeceğiz
        authService.requestAcademicianAccount(request);
        return ResponseEntity.ok("Academician account request received. Pending admin approval.");
    }

    // --- YENİ ENDPOINT: Kulüp Görevlisi Rol Talebi ---
    @PostMapping("/request/club-official")
    public ResponseEntity<String> requestClubOfficial() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        authService.requestClubOfficialRole(user.getId());
        return ResponseEntity.ok("Club official role request received. Pending admin approval.");
    }

    // --- Uyumluluk için: Path variable ile talep ---
    @PostMapping("/request/club-official/{userId}")
    public ResponseEntity<String> requestClubOfficialById(@PathVariable UUID userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // SecurityContext'teki principal bizim User detayımızdır
        User principal = (User) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        // Admin değilse sadece kendi adına talep atabilir
        if (!isAdmin && !principal.getId().equals(userId)) {
            throw new AccessDeniedException("You cannot request club-official role for another user.");
        }

        authService.requestClubOfficialRole(userId);
        return ResponseEntity.ok("Club official role request received. Pending admin approval.");
    }

    // --- YENİ ENDPOINT: ŞİFRE DEĞİŞTİRME ---
    /**
     * Giriş yapmış kullanıcının şifresini değiştirmesi için.
     * Bu endpoint, Adım 1'deki SecurityConfig sayesinde otomatik olarak korunur
     * (Token gerektirir).
     */
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication // Spring Security, token'dan kimliği doğrulanmış kullanıcıyı buraya inject eder
    ) {
        try {
            // 'authentication.getPrincipal()' bize 'User' (UserDetails) nesnesini verir
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            authService.changePassword(request, userDetails);

            return ResponseEntity.ok("Password changed successfully.");

        } catch (IllegalStateException e) {
            // "Mevcut şifre yanlış" veya "Şifreler eşleşmiyor" hatalarını yakala
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }

    // Toplu e-posta sorgulama (POST kullanıyoruz çünkü ID listesi uzun olabilir)
    @PostMapping("/users/emails")
    public ResponseEntity<List<String>> getEmailsByIds(@RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(authService.getEmailsByUserIds(userIds));
    }
}