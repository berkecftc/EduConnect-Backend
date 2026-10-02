package com.educonnect.authservices.controller;

import com.educonnect.authservices.dto.request.ChangePasswordRequest;
import com.educonnect.authservices.dto.request.ForgotPasswordRequest;
import com.educonnect.authservices.dto.request.LoginRequest;
import com.educonnect.authservices.dto.request.RefreshTokenRequest;
import com.educonnect.authservices.dto.request.RegisterRequest;
import com.educonnect.authservices.dto.request.ResendVerificationRequest;
import com.educonnect.authservices.dto.request.ResetPasswordRequest;
import com.educonnect.authservices.dto.request.VerifyEmailRequest;
import com.educonnect.authservices.service.EmailVerificationService;
import com.educonnect.authservices.dto.response.AuthResponse;
import com.educonnect.authservices.service.AuthSessionService;
import com.educonnect.authservices.service.PasswordService;
import com.educonnect.authservices.service.RegistrationService;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthSessionService authSessionService;
    private final RegistrationService registrationService;
    private final PasswordService passwordService;
    private final UserRepository userRepository;
    private final boolean openRegistrationEnabled;
    private final EmailVerificationService emailVerificationService;

    @Autowired
    public AuthController(AuthSessionService authSessionService,
                          RegistrationService registrationService,
                          PasswordService passwordService,
                          UserRepository userRepository,
                          @Value("${auth.registration.open-enabled:false}") boolean openRegistrationEnabled,
                          EmailVerificationService emailVerificationService) {
        this.authSessionService = authSessionService;
        this.registrationService = registrationService;
        this.passwordService = passwordService;
        this.userRepository = userRepository;
        this.openRegistrationEnabled = openRegistrationEnabled;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        if (!openRegistrationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Doğrudan kayıt kapalıdır. Lütfen öğrenci veya akademisyen başvurusu yapın.");
        }
        return ResponseEntity.ok(registrationService.register(request));
    }

    // --- YENİ ENDPOINT: Öğrenci Başvurusu (Belge ile) ---
    @PostMapping(value = "/request/student-account", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> requestStudentAccount(
            @Valid @RequestPart("request") RegisterRequest request,
            @RequestPart("studentDocument") MultipartFile studentDocument
    ) {
        registrationService.requestStudentAccount(request, studentDocument);
        return ResponseEntity.ok("Student account request received. Pending admin approval.");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        return ResponseEntity.ok(authSessionService.login(request));
    }

    // --- YENİ ENDPOINT: Akademisyen Başvurusu ---
    @PostMapping(value = "/request/academician-account", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> requestAcademicianAccount(
            @Valid @RequestPart("request") RegisterRequest request,
            @RequestPart("idCardImage") MultipartFile idCardImage
    ) {
        // Servis katmanında bu isteği işleyeceğiz (kimlik kartı fotoğrafı ile birlikte)
        registrationService.requestAcademicianAccount(request, idCardImage);
        return ResponseEntity.ok("Academician account request received. Pending admin approval.");
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        if (!emailVerificationService.verify(request.token())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_LINK_INVALID",
                    "Doğrulama bağlantısı geçersiz veya süresi dolmuş. Yeni bir bağlantı isteyin.");
        }
        return ResponseEntity.ok(Map.of("status", "VERIFIED", "message", "E-posta adresiniz doğrulandı."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request != null ? request.email() : null);
        return ResponseEntity.ok("E-posta adresi doğrulama bekliyorsa yeni bir doğrulama bağlantısı gönderildi.");
    }

    @PostMapping({"/request/club-official", "/request/club-official/{userId}"})
    public ResponseEntity<String> requestClubOfficial() {
        throw new ApiException(HttpStatus.GONE, "ENDPOINT_GONE",
                "Genel kulüp yetkilisi başvurusu kapatıldı. Kulüp görevleri kulüp kuruluş başvurusu ve danışman onaylı görev atamasıyla verilir.");
    }

    // --- YENİ ENDPOINT: ŞİFRE DEĞİŞTİRME ---
    /**
     * Giriş yapmış kullanıcının şifresini değiştirmesi için.
     * Bu endpoint, Adım 1'deki SecurityConfig sayesinde otomatik olarak korunur
     * (Token gerektirir).
     */
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication // Spring Security, token'dan kimliği doğrulanmış kullanıcıyı buraya inject eder
    ) {
        if (!(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Kimlik doğrulanamadı.");
        }

        passwordService.changePassword(request, userDetails);

        return ResponseEntity.ok("Password changed successfully.");
    }

    // --- YENİ ENDPOINT: REFRESH TOKEN ---
    /**
     * Refresh token ile yeni access token alır
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authSessionService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    // --- YENİ ENDPOINT: LOGOUT ---
    /**
     * Logout - refresh token'ı geçersiz kılar
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authSessionService.logout(request.refreshToken());
        return ResponseEntity.ok("Logged out successfully");
    }

    // --- YENİ ENDPOINT: ŞİFREMİ UNUTTUM ---
    /**
     * Kullanıcı şifresini unuttuğunda e-posta ile şifre sıfırlama linki gönderir.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            passwordService.forgotPassword(request);
        } catch (NoSuchElementException ignored) {
        }
        return ResponseEntity.ok("Bu e-posta adresi kayıtlıysa şifre sıfırlama linki gönderildi.");
    }

    // --- YENİ ENDPOINT: ŞİFRE SIFIRLAMA ---
    /**
     * Token ile şifre sıfırlama işlemini gerçekleştirir.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request);
        return ResponseEntity.ok("Şifreniz başarıyla sıfırlandı. Artık yeni şifrenizle giriş yapabilirsiniz.");
    }
}
