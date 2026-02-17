package com.educonnect.authservices.controller;

import com.educonnect.authservices.dto.request.ChangePasswordRequest;
import com.educonnect.authservices.dto.request.ForgotPasswordRequest;
import com.educonnect.authservices.dto.request.LoginRequest;
import com.educonnect.authservices.dto.request.RegisterRequest;
import com.educonnect.authservices.dto.request.ResetPasswordRequest;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

    // --- YENİ ENDPOINT: Öğrenci Başvurusu (Belge ile) ---
    @PostMapping(value = "/request/student-account", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> requestStudentAccount(
            @RequestPart("request") RegisterRequest request,
            @RequestPart("studentDocument") MultipartFile studentDocument
    ) {
        authService.requestStudentAccount(request, studentDocument);
        return ResponseEntity.ok("Student account request received. Pending admin approval.");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request
    ) {

        return ResponseEntity.ok(authService.login(request));
    }

    // --- YENİ ENDPOINT: Akademisyen Başvurusu ---
    @PostMapping(value = "/request/academician-account", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> requestAcademicianAccount(
            @RequestPart("request") RegisterRequest request,
            @RequestPart("idCardImage") MultipartFile idCardImage
    ) {
        // Servis katmanında bu isteği işleyeceğiz (kimlik kartı fotoğrafı ile birlikte)
        authService.requestAcademicianAccount(request, idCardImage);
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

    // --- YENİ ENDPOINT: REFRESH TOKEN ---
    /**
     * Refresh token ile yeni access token alır
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");
            if (refreshToken == null || refreshToken.isBlank()) {
                return ResponseEntity.badRequest().body("Refresh token is required");
            }
            AuthResponse response = authService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    // --- YENİ ENDPOINT: LOGOUT ---
    /**
     * Logout - refresh token'ı geçersiz kılar
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");
            if (refreshToken == null || refreshToken.isBlank()) {
                return ResponseEntity.badRequest().body("Refresh token is required");
            }
            authService.logout(refreshToken);
            return ResponseEntity.ok("Logged out successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Logout failed");
        }
    }

    // Toplu e-posta sorgulama (POST kullanıyoruz çünkü ID listesi uzun olabilir)
    @PostMapping("/users/emails")
    public ResponseEntity<List<String>> getEmailsByIds(@RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(authService.getEmailsByUserIds(userIds));
    }

    // --- YENİ ENDPOINT: ŞİFREMİ UNUTTUM ---
    /**
     * Kullanıcı şifresini unuttuğunda e-posta ile şifre sıfırlama linki gönderir.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            authService.forgotPassword(request);
            return ResponseEntity.ok("Şifre sıfırlama linki e-posta adresinize gönderildi.");
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Şifre sıfırlama işlemi sırasında bir hata oluştu.");
        }
    }

    // --- YENİ ENDPOINT: ŞİFRE SIFIRLAMA ---
    /**
     * Token ile şifre sıfırlama işlemini gerçekleştirir.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok("Şifreniz başarıyla sıfırlandı. Artık yeni şifrenizle giriş yapabilirsiniz.");
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Şifre sıfırlama işlemi sırasında bir hata oluştu.");
        }
    }
}