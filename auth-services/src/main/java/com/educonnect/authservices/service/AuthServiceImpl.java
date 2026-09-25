package com.educonnect.authservices.service;

import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.AcademicianProfileMessage;
import com.educonnect.authservices.dto.message.GamificationActionType;
import com.educonnect.authservices.dto.message.GamificationEventMessage;
import com.educonnect.authservices.dto.message.PasswordResetMessage;
import com.educonnect.authservices.dto.message.UserAccountStatusMessage;
import com.educonnect.authservices.dto.message.UserDeletedMessage;
import com.educonnect.authservices.dto.message.UserRegisteredMessage;
import com.educonnect.authservices.dto.request.ChangePasswordRequest;
import com.educonnect.authservices.dto.request.ForgotPasswordRequest;
import com.educonnect.authservices.dto.request.LoginRequest;
import com.educonnect.authservices.dto.request.RegisterRequest;
import com.educonnect.authservices.dto.request.ResetPasswordRequest;
import com.educonnect.authservices.dto.response.AcademicianRequestAdminView;
import com.educonnect.authservices.dto.response.AuthResponse;
import com.educonnect.authservices.dto.response.StudentRequestAdminView;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.educonnect.authservices.models.AcademicianRegistrationRequest; // YENİ IMPORT
import com.educonnect.authservices.models.PasswordResetToken;
import com.educonnect.authservices.models.StudentRegistrationRequest; // ÖĞRENCİ BAŞVURU
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.Repository.AcademicianRequestRepository; // YENİ IMPORT
import com.educonnect.authservices.Repository.PasswordResetTokenRepository;
import com.educonnect.authservices.Repository.StudentRequestRepository; // ÖĞRENCİ REPOSITORY
import com.educonnect.authservices.Repository.UserRepository;
import jakarta.transaction.Transactional; // Transaction yönetimi için
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.educonnect.authservices.config.AuthSecurityProperties;
import java.time.Instant;
import java.util.List;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.web.multipart.MultipartFile;

import static com.educonnect.authservices.config.RabbitMQConfig.ACADEMICIAN_ROUTING_KEY;
import static com.educonnect.authservices.config.RabbitMQConfig.EXCHANGE_NAME;

@Service
public class AuthServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final AcademicianRequestRepository requestRepository; // <-- YENİ EKLENTİ
    private final StudentRequestRepository studentRequestRepository; // <-- ÖĞRENCİ BAŞVURU
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;
    private final OutboxPublisher outboxPublisher;
    private final RefreshTokenService refreshTokenService;
    private final MinioService minioService; // Akademisyen kimlik kartı yüklemesi için
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;
    private final AuthSecurityProperties.Links links;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           AcademicianRequestRepository requestRepository, // Constructor'a eklendi
                           StudentRequestRepository studentRequestRepository, // Öğrenci repository
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JWTService jwtService,
                           AuthenticationManager authenticationManager,
                           OutboxPublisher outboxPublisher,
                           RefreshTokenService refreshTokenService,
                           MinioService minioService,
                           PasswordPolicy passwordPolicy,
                           LoginAttemptService loginAttemptService,
                           EmailVerificationService emailVerificationService,
                           AuthSecurityProperties authSecurityProperties) {
        this.passwordPolicy = passwordPolicy;
        this.loginAttemptService = loginAttemptService;
        this.emailVerificationService = emailVerificationService;
        this.links = authSecurityProperties.links();
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.outboxPublisher = outboxPublisher;
        this.refreshTokenService = refreshTokenService;
        this.minioService = minioService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() == null || request.getPassword() == null
                || request.getFirstName() == null || request.getLastName() == null) {
            throw new IllegalArgumentException("Missing required fields for registration");
        }
        passwordPolicy.validateNewPassword(request.getPassword(), request.getEmail());

        Set<Role> roles = Stream.of(Role.ROLE_STUDENT).collect(Collectors.toSet());

        var user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                roles
        );
        user.setEmailVerifiedAt(emailVerificationService.verifiedAtForNewAccount());

        User savedUser = userRepository.save(user);
        emailVerificationService.sendVerification(savedUser.getEmail(), request.getFirstName());

        Set<String> roleStrings = roles.stream().map(Role::name).collect(Collectors.toSet());

        UserRegisteredMessage message = new UserRegisteredMessage(
                savedUser.getId(),
                request.getFirstName(),
                request.getLastName(),
                savedUser.getEmail(),
                roleStrings,
                request.getStudentId(),
                request.getDepartment()
        );

        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        var jwtToken = jwtService.generateToken(savedUser);
        String refreshToken = refreshTokenService.issue(savedUser.getId());
        return buildAuthResponse(jwtToken, refreshToken, "User registered successfully.", savedUser);
    }

    // --- ÖĞRENCİ BAŞVURU İŞLEMİ ---
    @Transactional
    public void requestStudentAccount(RegisterRequest request, MultipartFile studentDocument) {

        // Email kontrolü - hem users hem de student_requests tablosunda kontrol et
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalStateException("Email already registered");
        }
        if (studentRequestRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalStateException("Bu email ile zaten bir başvuru mevcut");
        }

        // Öğrenci belgesi zorunlu
        if (studentDocument == null || studentDocument.isEmpty()) {
            throw new IllegalArgumentException("Öğrenci belgesi zorunludur");
        }
        passwordPolicy.validateNewPassword(request.getPassword(), request.getEmail());

        // 1. Öğrenci belgesini MinIO'ya yükle (geçici UUID ile)
        UUID tempId = UUID.randomUUID();
        String studentDocumentUrl = minioService.uploadStudentDocument(studentDocument, tempId);
        LOGGER.info("Öğrenci belgesi yüklendi: {}", studentDocumentUrl);

        // 2. Tüm bilgileri 'student_requests' tablosuna kaydet (users tablosuna KAYIT YAPILMIYOR!)
        StudentRegistrationRequest stuReq = new StudentRegistrationRequest();
        stuReq.setFirstName(request.getFirstName());
        stuReq.setLastName(request.getLastName());
        stuReq.setEmail(request.getEmail());
        stuReq.setPassword(passwordEncoder.encode(request.getPassword())); // Hashlenmiş şifre
        stuReq.setStudentNumber(request.getStudentId());
        stuReq.setDepartment(request.getDepartment());
        stuReq.setStudentDocumentUrl(studentDocumentUrl);
        stuReq.setEmailVerifiedAt(emailVerificationService.verifiedAtForNewAccount());

        studentRequestRepository.save(stuReq);
        emailVerificationService.sendVerification(request.getEmail(), request.getFirstName());

        LOGGER.info("Öğrenci başvurusu alındı; admin onayı bekleniyor.");
    }

    // --- ÖĞRENCİ ONAY İŞLEMİ ---
    @Transactional
    public void approveStudent(Long requestId) {
        // 1. Bekleyen başvuru detaylarını bul
        StudentRegistrationRequest req = studentRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Öğrenci başvuru formu bulunamadı!"));
        requireVerifiedEmail(req.getEmailVerifiedAt());

        // 2. Kullanıcıyı USERS tablosuna kaydet (ŞİMDİ kaydediyoruz!)
        Set<Role> roles = Stream.of(Role.ROLE_STUDENT).collect(Collectors.toSet());

        var user = new User(
                req.getEmail(),
                req.getPassword(), // Zaten hashlenmiş şifre
                roles
        );
        user.setEmailVerifiedAt(req.getEmailVerifiedAt() != null ? req.getEmailVerifiedAt() : Instant.now());

        User savedUser = userRepository.save(user);

        // 3. RabbitMQ mesajını gönder (User Service profil oluşturacak)
        Set<String> roleStrings = Stream.of(Role.ROLE_STUDENT.name()).collect(Collectors.toSet());

        UserRegisteredMessage message = new UserRegisteredMessage(
                savedUser.getId(),
                req.getFirstName(),
                req.getLastName(),
                req.getEmail(),
                roleStrings,
                req.getStudentNumber(),
                req.getDepartment(),
                req.getStudentDocumentUrl()
        );

        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        // 5. E-posta bildirimi gönder (onay)
        UserAccountStatusMessage statusMessage = new UserAccountStatusMessage(
                req.getEmail(),
                req.getFirstName(),
                req.getLastName(),
                "APPROVED",
                "STUDENT",
                null
        );
        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY,
                statusMessage
        );

        // 6. Temizlik: Başvuru isteğini sil
        studentRequestRepository.delete(req);

        LOGGER.info("Öğrenci onaylandı ve profil oluşturma mesajı gönderildi. UserID: {}", savedUser.getId());
    }

    // --- ÖĞRENCİ RED İŞLEMİ ---
    @Transactional
    public void rejectStudent(Long requestId, String rejectionReason) {
        // 1. Bekleyen başvuru detaylarını bul
        StudentRegistrationRequest req = studentRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Öğrenci başvuru formu bulunamadı!"));

        // 2. E-posta bildirimi gönder (red) - Silmeden önce bilgileri al
        UserAccountStatusMessage statusMessage = new UserAccountStatusMessage(
                req.getEmail(),
                req.getFirstName(),
                req.getLastName(),
                "REJECTED",
                "STUDENT",
                rejectionReason
        );
        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY,
                statusMessage
        );
        LOGGER.info("Öğrenci red bildirimi RabbitMQ'ya gönderildi. RoutingKey: {}", RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY);

        // 3. MinIO'dan belgeyi sil
        minioService.deleteStudentDocument(req.getStudentDocumentUrl());

        // 4. Başvuru kaydını sil (users tablosunda kayıt yok, silmeye gerek yok)
        studentRequestRepository.delete(req);

        LOGGER.info("Öğrenci başvurusu reddedildi. RequestId: {}", req.getId());
    }

    // --- TÜM ÖĞRENCİ BAŞVURULARINI LİSTELE ---
    public List<StudentRequestAdminView> getAllStudentRequests() {
        return studentRequestRepository.findAll().stream()
                .map(req -> new StudentRequestAdminView(
                        req.getId(),
                        req.getFirstName(),
                        req.getLastName(),
                        req.getEmail(),
                        req.getStudentNumber(),
                        req.getDepartment(),
                        minioService.createPresignedUrl(req.getStudentDocumentUrl()),
                        req.getEmailVerifiedAt() != null
                ))
                .toList();
    }

    // --- AKADEMİSYEN BAŞVURU İŞLEMİ (DÜZELTİLDİ) ---
    @Transactional // Transactional önemli: İki tabloya birden yazıyoruz
    public void requestAcademicianAccount(RegisterRequest request, MultipartFile idCardImage) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalStateException("Email already registered");
        }

        // Kimlik kartı fotoğrafı zorunlu
        if (idCardImage == null || idCardImage.isEmpty()) {
            throw new IllegalArgumentException("Akademisyen kimlik kartı fotoğrafı zorunludur");
        }
        passwordPolicy.validateNewPassword(request.getPassword(), request.getEmail());

        // 1. Kullanıcıyı 'PENDING' rolüyle USERS tablosuna kaydet
        Set<Role> roles = Stream.of(Role.ROLE_PENDING_ACADEMICIAN).collect(Collectors.toSet());

        var user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                roles
        );
        user.setEmailVerifiedAt(emailVerificationService.verifiedAtForNewAccount());

        User savedUser = userRepository.save(user); // Önce User ID oluşsun

        // 2. Kimlik kartı fotoğrafını MinIO'ya yükle
        String idCardImageUrl = minioService.uploadIdCardImage(idCardImage, savedUser.getId());
        LOGGER.info("Akademisyen kimlik kartı yüklendi: {}", idCardImageUrl);

        // 3. Detaylı bilgileri 'academician_requests' tablosuna kaydet
        // (Böylece veriler admin onaylayana kadar burada güvende kalır)
        AcademicianRegistrationRequest accReq = new AcademicianRegistrationRequest();
        accReq.setUserId(savedUser.getId());
        accReq.setFirstName(request.getFirstName());
        accReq.setLastName(request.getLastName());
        accReq.setTitle(request.getTitle());
        accReq.setDepartment(request.getDepartment());
        accReq.setOfficeNumber(request.getOfficeNumber());
        accReq.setIdCardImageUrl(idCardImageUrl); // Kimlik kartı URL'sini kaydet

        requestRepository.save(accReq);
        emailVerificationService.sendVerification(savedUser.getEmail(), request.getFirstName());

        LOGGER.info("Akademisyen başvurusu alındı. UserID: {}", savedUser.getId());
        // DİKKAT: Burada RabbitMQ mesajı GÖNDERMİYORUZ. Onay bekliyor.
    }

    // --- AKADEMİSYEN ONAY İŞLEMİ (DÜZELTİLDİ) ---
    @Transactional
    public void approveAcademician(UUID userId) {
        // 1. Kullanıcıyı bul
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        requireVerifiedEmail(user.getEmailVerifiedAt());

        // 2. Bekleyen başvuru detaylarını (Unvan, Bölüm vs.) bul
        AcademicianRegistrationRequest req = requestRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Başvuru formu bulunamadı!"));

        // 3. Rolünü güncelle (PENDING -> ACADEMICIAN)
        Set<Role> roles = user.getRoles();
        // Null check veya doğrudan remove/add
        if (roles.contains(Role.ROLE_PENDING_ACADEMICIAN)) {
            roles.remove(Role.ROLE_PENDING_ACADEMICIAN);
            roles.add(Role.ROLE_ACADEMICIAN);
            user.setRoles(roles);
            userRepository.save(user);
        } else {
            // Zaten onaylı veya yanlış rol durumu için log atılabilir
            LOGGER.warn("Kullanıcı zaten PENDING rolünde değil veya işlem hatalı: {}", userId);
        }

        // 4. ŞİMDİ RABBITMQ MESAJINI GÖNDER! (User Service bunu bekliyor)
        // Request tablosundaki verileri kullanıyoruz
        AcademicianProfileMessage profileMessage = new AcademicianProfileMessage(
                user.getId(),
                req.getFirstName(),
                req.getLastName(),
                user.getEmail(),
                req.getTitle(),
                req.getDepartment(),
                req.getOfficeNumber(),
                req.getIdCardImageUrl() // Kimlik kartı fotoğrafı URL'si
        );

        outboxPublisher.publish(EXCHANGE_NAME, ACADEMICIAN_ROUTING_KEY, profileMessage);

        // 5. E-posta bildirimi gönder (onay)
        UserAccountStatusMessage statusMessage = new UserAccountStatusMessage(
                user.getEmail(),
                req.getFirstName(),
                req.getLastName(),
                "APPROVED",
                "ACADEMICIAN",
                null
        );
        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY,
                statusMessage
        );

        // 6. Temizlik: Başvuru isteğini sil (Artık işi bitti)
        requestRepository.delete(req);

        LOGGER.info("Akademisyen onaylandı ve profil oluşturma mesajı gönderildi. UserID: {}", userId);
    }


    public AuthResponse login(LoginRequest loginRequest) {
        loginAttemptService.ensureNotLocked(loginRequest.getEmail());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(loginRequest.getEmail());
            throw e;
        }

        // 2. Kullanıcıyı veritabanından çek
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("Error: User not found."));

        // --- BEKLEME KONTROLÜ ---
        // Eğer kullanıcının rolleri arasında "ROLE_PENDING_ACADEMICIAN" varsa hata fırlat!
        // NOT: ROLE_PENDING_STUDENT artık users tablosunda olmayacak, sadece student_requests tablosunda
        boolean isPendingAcademician = user.getRoles().stream()
                .anyMatch(role -> role.name().equals("ROLE_PENDING_ACADEMICIAN"));

        if (isPendingAcademician) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Hesabınız henüz onaylanmadı. Lütfen yönetici onayını bekleyin.");
        }
        // ---------------------------------------------
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Hesabınız askıya alınmıştır. Ayrıntılı bilgi için yönetici ile iletişime geçin.");
        }
        if (!emailVerificationService.isVerified(user.getEmailVerifiedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "E-posta adresinizi doğrulamanız gerekiyor. Gelen kutunuzdaki doğrulama bağlantısını kullanın.");
        }

        loginAttemptService.recordSuccess(user.getId());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());

        LocalDate istanbulToday = LocalDate.now(ZoneId.of("Europe/Istanbul"));
        String referenceId = "LOGIN:" + istanbulToday + ":" + user.getId();
        GamificationEventMessage gamificationEvent = new GamificationEventMessage(
                user.getId(),
                GamificationActionType.DAILY_LOGIN,
                referenceId,
                OffsetDateTime.now(ZoneId.of("Europe/Istanbul"))
        );
        if (user.getRoles().contains(Role.ROLE_STUDENT)) {
            outboxPublisher.publish(
                    RabbitMQConfig.GAMIFICATION_EXCHANGE,
                    RabbitMQConfig.GAMIFICATION_USER_LOGIN_ROUTING_KEY,
                    gamificationEvent
            );
        }

        return buildAuthResponse(jwt, refreshToken, "Login successful", user);
    }

    // ---- Kulüp Görevlisi Başvuru Akışı ----

    /**
     * Admin kulüp görevlisi talebini kabul eder.
     * ROLE_PENDING_CLUB_OFFICIAL kaldırılır, ROLE_CLUB_OFFICIAL eklenir.
     * Onaydan sonra profil senkronizasyonu için mesaj gönderilebilir (opsiyonel).
     */
    public void approveClubOfficial(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Set<Role> roles = user.getRoles();
        if (!roles.contains(Role.ROLE_PENDING_CLUB_OFFICIAL)) {
            throw new IllegalStateException("User does not have a pending club official request");
        }
        roles.remove(Role.ROLE_PENDING_CLUB_OFFICIAL);
        roles.add(Role.ROLE_CLUB_OFFICIAL);
        user.setRoles(roles);
        userRepository.save(user);
    }

    /**
     * Admin kulüp görevlisi talebini reddeder.
     * ROLE_PENDING_CLUB_OFFICIAL rolü kaldırılır, diğer roller korunur.
     */
    public void rejectClubOfficial(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Set<Role> roles = user.getRoles();
        if (!roles.contains(Role.ROLE_PENDING_CLUB_OFFICIAL)) {
            // İstemciye bilgi; idempotent de davranılabilir
            throw new IllegalStateException("User does not have a pending club official request");
        }
        roles.remove(Role.ROLE_PENDING_CLUB_OFFICIAL);
        user.setRoles(roles);
        userRepository.save(user);
    }

    // ---- Admin Yönetimi ----
    public void promoteToAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        // Admin rolü sadece tek başına olmalı - diğer rolleri temizle
        Set<Role> roles = Stream.of(Role.ROLE_ADMIN).collect(Collectors.toSet());
        user.setRoles(roles);
        userRepository.save(user);

        LOGGER.info("User promoted to ADMIN (all other roles removed). UserID: {}", userId);
    }

    public void revokeAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        Set<Role> roles = user.getRoles();
        if (roles.contains(Role.ROLE_ADMIN)) {
            roles.remove(Role.ROLE_ADMIN);
            // Admin rolü kaldırılınca kullanıcının hiç rolü kalmazsa STUDENT yap
            if (roles.isEmpty()) {
                roles.add(Role.ROLE_STUDENT);
                LOGGER.info("Admin role revoked, user set to ROLE_STUDENT. UserID: {}", userId);
            }
            user.setRoles(roles);
            userRepository.save(user);
        }
    }

    // --- YENİ METOT: ŞİFRE DEĞİŞTİRME ---
    /**
     * Giriş yapmış kullanıcının şifresini değiştirir.
     * @param request Mevcut ve yeni şifreleri içeren DTO
     * @param authenticatedUser Giriş yapmış kullanıcının (token'dan gelen) UserDetails objesi
     */
    public void changePassword(ChangePasswordRequest request, UserDetails authenticatedUser) {

        // 1. Veritabanından en güncel kullanıcıyı al
        User user = userRepository.findByEmail(authenticatedUser.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Mevcut şifre doğru mu diye kontrol et
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalStateException("Wrong current password");
            // (Daha iyisi: 400 Bad Request hatası fırlat)
        }

        // 3. Yeni şifre ve onayı eşleşiyor mu diye kontrol et
        if (!request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new IllegalStateException("New password and confirmation do not match");
        }

        // 4. (Opsiyonel) Yeni şifre, eski şifreyle aynı olamaz kontrolü
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalStateException("New password cannot be the same as the old password");
        }
        passwordPolicy.validateNewPassword(request.getNewPassword(), user.getEmail());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllSessions(user.getId());
    }

    public List<String> getEmailsByUserIds(List<UUID> userIds) {
        return userRepository.findEmailsByIds(userIds);
    }

    public AuthResponse refreshAccessToken(String refreshTokenStr) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(refreshTokenStr);

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new RefreshTokenService.InvalidRefreshTokenException("Invalid refresh token"));
        if (user.isSuspended()) {
            refreshTokenService.revokeAllSessions(user.getId());
            throw new RefreshTokenService.InvalidRefreshTokenException("Account is suspended");
        }

        String newAccessToken = jwtService.generateToken(user);
        return buildAuthResponse(newAccessToken, rotated.rawToken(), "Token refreshed successfully", user);
    }

    private void requireVerifiedEmail(Instant emailVerifiedAt) {
        if (!emailVerificationService.isVerified(emailVerifiedAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Başvuru sahibi e-posta adresini henüz doğrulamadı.");
        }
    }

    private AuthResponse buildAuthResponse(String token, String refreshToken, String message, User user) {
        Set<Role> roles = user.getRoles() != null ? user.getRoles() : Set.of();
        return new AuthResponse(token, refreshToken, message, user.getId().toString(), user.getEmail(),
                RolePresentation.orderedRoles(roles), RolePresentation.primaryRole(roles),
                RolePresentation.pendingRequests(roles));
    }

    public void logout(String refreshToken) {
        refreshTokenService.revokeSession(refreshToken);
    }

    // ... Diğer metodlar ...

    // 1. BEKLEYEN AKADEMİSYEN İSTEKLERİNİ LİSTELE
    public List<AcademicianRequestAdminView> getAllAcademicianRequests() {
        return requestRepository.findAll().stream()
                .map(req -> new AcademicianRequestAdminView(
                        req.getId(),
                        req.getUserId(),
                        req.getFirstName(),
                        req.getLastName(),
                        req.getTitle(),
                        req.getDepartment(),
                        req.getOfficeNumber(),
                        minioService.createPresignedUrl(req.getIdCardImageUrl()),
                        userRepository.findById(req.getUserId())
                                .map(u -> u.getEmailVerifiedAt() != null)
                                .orElse(false)
                ))
                .toList();
    }

    // 2. AKADEMİSYEN İSTEĞİNİ REDDET
    @Transactional
    public void rejectAcademician(UUID userId, String rejectionReason) {
        // Kullanıcıyı bul
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        // İsteği bul
        AcademicianRegistrationRequest req = requestRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Request not found"));

        // Kullanıcının sadece PENDING_ACADEMICIAN rolü olduğunu doğrula
        Set<Role> roles = user.getRoles();
        if (!roles.contains(Role.ROLE_PENDING_ACADEMICIAN)) {
            throw new IllegalStateException("User does not have a pending academician request");
        }

        // 1. E-posta bildirimi gönder (red) - Silmeden önce bilgileri al
        UserAccountStatusMessage statusMessage = new UserAccountStatusMessage(
                user.getEmail(),
                req.getFirstName(),
                req.getLastName(),
                "REJECTED",
                "ACADEMICIAN",
                rejectionReason
        );
        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY,
                statusMessage
        );
        LOGGER.info("Akademisyen red bildirimi RabbitMQ'ya gönderildi. RoutingKey: {}", RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY);

        // 2. MinIO'dan kimlik kartı fotoğrafını sil
        if (req.getIdCardImageUrl() != null) {
            minioService.deleteIdCardImage(req.getIdCardImageUrl());
        }

        // 3. İstek tablosundan veriyi sil
        requestRepository.delete(req);

        // 4. Kullanıcıyı users tablosundan tamamen sil
        // (Sadece başvuru için oluşturulduğundan, reddedilince silinmeli)
        userRepository.delete(user);

        LOGGER.info("Akademisyen başvurusu reddedildi ve kullanıcı silindi. UserID: {}", userId);
    }

    // 1. TÜM KULLANICILARI GETİR
    public List<com.educonnect.authservices.dto.response.UserSummaryDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> new com.educonnect.authservices.dto.response.UserSummaryDto(
                        user.getId(),
                        user.getEmail(),
                        user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                        user.getStatus() != null ? user.getStatus().name() : null,
                        user.getEmailVerifiedAt() != null
                ))
                .collect(Collectors.toList());
    }

    // 2. KULLANICIYI SİL (Yasaklama/Banlama)
    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("Kullanıcı bulunamadı"));

        // Kullanıcının rolüne göre mesaj tipini belirle
        String userType = "UNKNOWN";
        if (user.getRoles().contains(Role.ROLE_STUDENT)) {
            userType = "STUDENT";
        } else if (user.getRoles().contains(Role.ROLE_ACADEMICIAN)) {
            userType = "ACADEMICIAN";
        }

        // User Service'e silme mesajı gönder (arşivleme için)
        UserDeletedMessage message = new UserDeletedMessage(
            userId,
            userType,
            "Admin tarafından silindi"
        );

        outboxPublisher.publish(
            RabbitMQConfig.EXCHANGE_NAME,
            RabbitMQConfig.USER_DELETE_ROUTING_KEY,
            message
        );
        LOGGER.info("User deletion message queued. UserID: {}, Type: {}", userId, userType);

        requestRepository.findByUserId(userId).ifPresent(request -> {
            requestRepository.delete(request);
            String idCardImageUrl = request.getIdCardImageUrl();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    minioService.deleteIdCardImage(idCardImageUrl);
                }
            });
        });
        studentRequestRepository.findByEmail(user.getEmail()).ifPresent(studentRequestRepository::delete);
        emailVerificationService.discardTokens(user.getEmail());

        // Auth DB'den kullanıcıyı sil
        userRepository.deleteById(userId);
        LOGGER.info("User deleted from auth_db. UserID: {}", userId);
    }

    // --- ŞİFREMİ UNUTTUM İŞLEMİ ---
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail();

        // 1. Kullanıcıyı bul
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Bu email adresi ile kayıtlı kullanıcı bulunamadı."));

        // 2. Önceki tokenları temizle
        passwordResetTokenRepository.deleteByUserId(user.getId());

        String token = OpaqueTokens.generate();

        PasswordResetToken resetToken = new PasswordResetToken(
                OpaqueTokens.hash(token),
                user.getId(),
                java.time.Instant.now().plusSeconds(15 * 60) // 15 dakika
        );
        passwordResetTokenRepository.save(resetToken);

        String resetLink = links.frontendBaseUrl() + "/reset-password?token=" + token;

        // 6. RabbitMQ ile notification-service'e mesaj gönder
        PasswordResetMessage message = new PasswordResetMessage(
                user.getEmail(),
                null, // firstName - user-service'ten alınabilir, şimdilik null
                null, // lastName - user-service'ten alınabilir, şimdilik null
                token,
                resetLink
        );

        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.PASSWORD_RESET_ROUTING_KEY,
                message
        );

        LOGGER.info("Şifre sıfırlama e-postası kuyruğa alındı. UserID: {}", user.getId());
    }

    // --- ŞİFRE SIFIRLAMA İŞLEMİ ---
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String token = request.getToken();
        String newPassword = request.getNewPassword();
        String confirmPassword = request.getConfirmPassword();

        // 1. Token validasyonu
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token gereklidir.");
        }

        // 2. Şifre eşleşme kontrolü
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalStateException("Şifreler eşleşmiyor.");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(OpaqueTokens.hash(token))
                .orElseThrow(() -> new NoSuchElementException("Geçersiz veya süresi dolmuş token."));

        // 5. Token süre kontrolü
        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new IllegalStateException("Token süresi dolmuş. Lütfen yeni bir şifre sıfırlama talebi oluşturun.");
        }

        // 6. Kullanıcıyı bul
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Kullanıcı bulunamadı."));
        passwordPolicy.validateResetPassword(newPassword, user.getEmail());

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllSessions(user.getId());
        loginAttemptService.recordSuccess(user.getId());

        // 8. Kullanılan token'ı sil
        passwordResetTokenRepository.delete(resetToken);

        LOGGER.info("Şifre başarıyla sıfırlandı. UserID: {}", user.getId());
    }
}
