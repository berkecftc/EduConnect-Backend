package com.educonnect.authservices.service;

import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.AcademicianProfileMessage;
import com.educonnect.authservices.dto.message.UserDeletedMessage;
import com.educonnect.authservices.dto.message.UserRegisteredMessage;
import com.educonnect.authservices.dto.request.ChangePasswordRequest;
import com.educonnect.authservices.dto.request.LoginRequest;
import com.educonnect.authservices.dto.request.RegisterRequest;
import com.educonnect.authservices.dto.response.AuthResponse;
import com.educonnect.authservices.models.AcademicianRegistrationRequest; // YENİ IMPORT
import com.educonnect.authservices.models.StudentRegistrationRequest; // ÖĞRENCİ BAŞVURU
import com.educonnect.authservices.models.RefreshToken;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.Repository.AcademicianRequestRepository; // YENİ IMPORT
import com.educonnect.authservices.Repository.StudentRequestRepository; // ÖĞRENCİ REPOSITORY
import com.educonnect.authservices.Repository.UserRepository;
import jakarta.transaction.Transactional; // Transaction yönetimi için
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RabbitTemplate rabbitTemplate;
    private final RefreshTokenService refreshTokenService;
    private final MinioService minioService; // Akademisyen kimlik kartı yüklemesi için

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           AcademicianRequestRepository requestRepository, // Constructor'a eklendi
                           StudentRequestRepository studentRequestRepository, // Öğrenci repository
                           PasswordEncoder passwordEncoder,
                           JWTService jwtService,
                           AuthenticationManager authenticationManager,
                           RabbitTemplate rabbitTemplate,
                           RefreshTokenService refreshTokenService,
                           MinioService minioService) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.rabbitTemplate = rabbitTemplate;
        this.refreshTokenService = refreshTokenService;
        this.minioService = minioService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() == null || request.getPassword() == null
                || request.getFirstName() == null || request.getLastName() == null) {
            throw new IllegalArgumentException("Missing required fields for registration");
        }

        Set<Role> roles = Stream.of(Role.ROLE_STUDENT).collect(Collectors.toSet());

        var user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                roles
        );

        User savedUser = userRepository.save(user);

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

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        var jwtToken = jwtService.generateToken(savedUser);
        var refreshToken = refreshTokenService.createRefreshToken(savedUser.getId());
        Set<String> userRoles = savedUser.getRoles().stream()
                .sorted((r1, r2) -> {
                    if (r1.name().equals("ROLE_ADMIN")) return -1;
                    if (r2.name().equals("ROLE_ADMIN")) return 1;
                    return r1.name().compareTo(r2.name());
                })
                .map(Role::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return new AuthResponse(jwtToken, refreshToken.getToken(), "User registered successfully.",
                savedUser.getId().toString(), savedUser.getEmail(), userRoles);
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

        // DEBUG LOG
        LOGGER.info("DEBUG - Student Request Data: firstName={}, lastName={}, email={}, studentId={}, department={}",
                request.getFirstName(), request.getLastName(), request.getEmail(),
                request.getStudentId(), request.getDepartment());

        studentRequestRepository.save(stuReq);

        LOGGER.info("Öğrenci başvurusu alındı. Email: {} - Admin onayı bekleniyor.", request.getEmail());
    }

    // --- ÖĞRENCİ ONAY İŞLEMİ ---
    @Transactional
    public void approveStudent(Long requestId) {
        // 1. Bekleyen başvuru detaylarını bul
        StudentRegistrationRequest req = studentRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Öğrenci başvuru formu bulunamadı!"));

        // 2. Kullanıcıyı USERS tablosuna kaydet (ŞİMDİ kaydediyoruz!)
        Set<Role> roles = Stream.of(Role.ROLE_STUDENT).collect(Collectors.toSet());

        var user = new User(
                req.getEmail(),
                req.getPassword(), // Zaten hashlenmiş şifre
                roles
        );

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

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        // 4. Temizlik: Başvuru isteğini sil
        studentRequestRepository.delete(req);

        LOGGER.info("Öğrenci onaylandı ve profil oluşturma mesajı gönderildi. UserID: {}", savedUser.getId());
    }

    // --- ÖĞRENCİ RED İŞLEMİ ---
    @Transactional
    public void rejectStudent(Long requestId) {
        // 1. Bekleyen başvuru detaylarını bul
        StudentRegistrationRequest req = studentRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Öğrenci başvuru formu bulunamadı!"));

        // 2. MinIO'dan belgeyi sil
        minioService.deleteStudentDocument(req.getStudentDocumentUrl());

        // 3. Başvuru kaydını sil (users tablosunda kayıt yok, silmeye gerek yok)
        studentRequestRepository.delete(req);

        LOGGER.info("Öğrenci başvurusu reddedildi. Email: {}", req.getEmail());
    }

    // --- TÜM ÖĞRENCİ BAŞVURULARINI LİSTELE ---
    public List<StudentRegistrationRequest> getAllStudentRequests() {
        return studentRequestRepository.findAll();
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

        // 1. Kullanıcıyı 'PENDING' rolüyle USERS tablosuna kaydet
        Set<Role> roles = Stream.of(Role.ROLE_PENDING_ACADEMICIAN).collect(Collectors.toSet());

        var user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                roles
        );

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

        LOGGER.info("Akademisyen başvurusu alındı. UserID: {}", savedUser.getId());
        // DİKKAT: Burada RabbitMQ mesajı GÖNDERMİYORUZ. Onay bekliyor.
    }

    // --- AKADEMİSYEN ONAY İŞLEMİ (DÜZELTİLDİ) ---
    @Transactional
    public void approveAcademician(UUID userId) {
        // 1. Kullanıcıyı bul
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

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
                req.getTitle(),
                req.getDepartment(),
                req.getOfficeNumber(),
                req.getIdCardImageUrl() // Kimlik kartı fotoğrafı URL'si
        );

        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ACADEMICIAN_ROUTING_KEY, profileMessage);

        // 5. Temizlik: Başvuru isteğini sil (Artık işi bitti)
        requestRepository.delete(req);

        LOGGER.info("Akademisyen onaylandı ve profil oluşturma mesajı gönderildi. UserID: {}", userId);
    }


    public AuthResponse login(LoginRequest loginRequest) {
        // 1. Önce kimlik doğrulaması (Email & Şifre kontrolü)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        // 2. Kullanıcıyı veritabanından çek
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("Error: User not found."));

        // --- BEKLEME KONTROLÜ ---
        // Eğer kullanıcının rolleri arasında "ROLE_PENDING_ACADEMICIAN" varsa hata fırlat!
        // NOT: ROLE_PENDING_STUDENT artık users tablosunda olmayacak, sadece student_requests tablosunda
        boolean isPendingAcademician = user.getRoles().stream()
                .anyMatch(role -> role.name().equals("ROLE_PENDING_ACADEMICIAN"));

        if (isPendingAcademician) {
            throw new RuntimeException("Hesabınız henüz onaylanmadı. Lütfen yönetici onayını bekleyin.");
        }
        // ---------------------------------------------

        // 3. Her şey yolundaysa Token üret
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtService.generateToken(user);

        // Refresh token oluştur
        var refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Tüm rolleri al ve ROLE_ADMIN'i önce koy
        Set<String> roles = user.getRoles().stream()
                .sorted((r1, r2) -> {
                    // ROLE_ADMIN önce gelsin
                    if (r1.name().equals("ROLE_ADMIN")) return -1;
                    if (r2.name().equals("ROLE_ADMIN")) return 1;
                    return r1.name().compareTo(r2.name());
                })
                .map(Role::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return new AuthResponse(jwt, refreshToken.getToken(), "Login successful",
                user.getId().toString(), user.getEmail(), roles);
    }

    // ---- Kulüp Görevlisi Başvuru Akışı ----

    /**
     * Var olan kullanıcı (örn: ROLE_STUDENT) kulüp görevlisi olmak için başvurur.
     * Kullanıcının rollerine ROLE_PENDING_CLUB_OFFICIAL eklenir.
     */
    public void requestClubOfficialRole(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Set<Role> roles = user.getRoles();
        if (roles.contains(Role.ROLE_CLUB_OFFICIAL)) {
            throw new IllegalStateException("User is already a club official");
        }
        if (roles.contains(Role.ROLE_PENDING_CLUB_OFFICIAL)) {
            // idempotent davran; ikinci kez ekleme
            return;
        }
        roles.add(Role.ROLE_PENDING_CLUB_OFFICIAL);
        user.setRoles(roles);
        userRepository.save(user);
    }

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

        // Opsiyonel: Profili güncellemek/rol senkronu için mesaj gönder
        String firstName = user.getEmail().split("@")[0];
        String lastName = "ClubOfficial";
        sendMessageToUserQueue(user, firstName, lastName, roles);
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

        // 5. Her şey yolundaysa, yeni şifreyi HASH'le ve kaydet
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // NOT: (İleri Seviye Güvenlik)
        // Şifre değiştiğinde, bu kullanıcıya ait diğer tüm JWT token'ları
        // geçersiz kılmak için bir mekanizma (örn: 'passwordChangedAt' timestamp'i)
        // eklenebilir. Şimdilik bu adımı atlıyoruz.
    }

    public List<String> getEmailsByUserIds(List<UUID> userIds) {
        return userRepository.findEmailsByIds(userIds);
    }

    /**
     * Refresh token ile yeni access token oluşturur
     */
    public AuthResponse refreshAccessToken(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenService.findByToken(refreshTokenStr);
        refreshTokenService.verifyExpiration(refreshToken);

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = jwtService.generateToken(user);
        Set<String> roles = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toSet());

        return new AuthResponse(newAccessToken, refreshTokenStr, "Token refreshed successfully",
                user.getId().toString(), user.getEmail(), roles);
    }

    /**
     * Logout - refresh token'ı siler
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.deleteByToken(refreshToken);
        LOGGER.info("User logged out, refresh token deleted");
    }

    /**
     * Helper metod: User-service'e kullanıcı profil güncelleme mesajı gönderir.
     */
    private void sendMessageToUserQueue(User user, String firstName, String lastName, Set<Role> roles) {
        Set<String> roleStrings = roles.stream().map(Role::name).collect(Collectors.toSet());

        UserRegisteredMessage message = new UserRegisteredMessage(
                user.getId(),
                firstName,
                lastName,
                user.getEmail(),
                roleStrings,
                null, // studentId yok
                null  // department yok
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        LOGGER.info("User profile update message sent for user: {}", user.getId());
    }

    // ... Diğer metodlar ...

    // 1. BEKLEYEN AKADEMİSYEN İSTEKLERİNİ LİSTELE
    public List<AcademicianRegistrationRequest> getAllAcademicianRequests() {
        return requestRepository.findAll();
    }

    // 2. AKADEMİSYEN İSTEĞİNİ REDDET
    @Transactional
    public void rejectAcademician(UUID userId) {
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

        // 1. MinIO'dan kimlik kartı fotoğrafını sil
        if (req.getIdCardImageUrl() != null) {
            minioService.deleteIdCardImage(req.getIdCardImageUrl());
        }

        // 2. İstek tablosundan veriyi sil
        requestRepository.delete(req);

        // 3. Kullanıcıyı users tablosundan tamamen sil
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
                        user.getRoles().stream().map(Enum::name).collect(Collectors.toSet())
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

        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.USER_DELETE_ROUTING_KEY,
                message
            );
            LOGGER.info("User deletion message sent to queue. UserID: {}, Type: {}", userId, userType);
        } catch (Exception e) {
            LOGGER.error("Failed to send user deletion message for UserID: {}. Error: {}", userId, e.getMessage());
        }

        // Auth DB'den kullanıcıyı sil
        userRepository.deleteById(userId);
        LOGGER.info("User deleted from auth_db. UserID: {}", userId);
    }
}
