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
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.Repository.AcademicianRequestRepository; // YENİ IMPORT
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

import static com.educonnect.authservices.config.RabbitMQConfig.ACADEMICIAN_ROUTING_KEY;
import static com.educonnect.authservices.config.RabbitMQConfig.EXCHANGE_NAME;

@Service
public class AuthServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final AcademicianRequestRepository requestRepository; // <-- YENİ EKLENTİ
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           AcademicianRequestRepository requestRepository, // Constructor'a eklendi
                           PasswordEncoder passwordEncoder,
                           JWTService jwtService,
                           AuthenticationManager authenticationManager,
                           RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.rabbitTemplate = rabbitTemplate;
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
        return new AuthResponse(jwtToken, "User registered successfully.");
    }

    public AuthResponse registerStudent(RegisterRequest request) {
        // ... (Aynı mantık, validasyonlar vs.)
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalStateException("Email already registered");
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
        return new AuthResponse(jwtToken, "Student registered successfully.");
    }

    // --- AKADEMİSYEN BAŞVURU İŞLEMİ (DÜZELTİLDİ) ---
    @Transactional // Transactional önemli: İki tabloya birden yazıyoruz
    public void requestAcademicianAccount(RegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalStateException("Email already registered");
        }

        // 1. Kullanıcıyı 'PENDING' rolüyle USERS tablosuna kaydet
        Set<Role> roles = Stream.of(Role.ROLE_PENDING_ACADEMICIAN).collect(Collectors.toSet());

        var user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                roles
        );

        User savedUser = userRepository.save(user); // Önce User ID oluşsun

        // 2. Detaylı bilgileri 'academician_requests' tablosuna kaydet
        // (Böylece veriler admin onaylayana kadar burada güvende kalır)
        AcademicianRegistrationRequest accReq = new AcademicianRegistrationRequest();
        accReq.setUserId(savedUser.getId());
        accReq.setFirstName(request.getFirstName());
        accReq.setLastName(request.getLastName());
        accReq.setTitle(request.getTitle());
        accReq.setDepartment(request.getDepartment());
        accReq.setOfficeNumber(request.getOfficeNumber());

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
                req.getOfficeNumber()
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

        // --- YENİ EKLENEN KISIM: BEKLEME KONTROLÜ ---
        // Eğer kullanıcının rolleri arasında "ROLE_PENDING_ACADEMICIAN" varsa hata fırlat!
        boolean isPending = user.getRoles().stream()
                .anyMatch(role -> role.name().equals("ROLE_PENDING_ACADEMICIAN"));

        if (isPending) {
            throw new RuntimeException("Hesabınız henüz onaylanmadı. Lütfen yönetici onayını bekleyin.");
        }
        // ---------------------------------------------

        // 3. Her şey yolundaysa Token üret
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtService.generateToken(user);

        // ... geri kalanı aynı
        return new AuthResponse(jwt, "Login successful");
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
        Set<Role> roles = user.getRoles();
        if (!roles.contains(Role.ROLE_ADMIN)) {
            roles.add(Role.ROLE_ADMIN);
            user.setRoles(roles);
            userRepository.save(user);
        }
    }

    public void revokeAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        Set<Role> roles = user.getRoles();
        if (roles.contains(Role.ROLE_ADMIN)) {
            roles.remove(Role.ROLE_ADMIN);
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
     * Helper metod: User-service'e kullanıcı profil güncelleme mesajı gönderir.
     */
    private void sendMessageToUserQueue(User user, String firstName, String lastName, Set<Role> roles) {
        Set<String> roleStrings = roles.stream().map(Role::name).collect(Collectors.toSet());

        UserRegisteredMessage message = new UserRegisteredMessage(
                user.getId(),
                firstName,
                lastName,
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

        // Rolünü PENDING durumundan çıkar (veya komple User'ı sil)
        // YÖNTEM A: Sadece rolü sil (User kalır ama yetkisiz olur)
        Set<Role> roles = user.getRoles();
        if (roles.contains(Role.ROLE_PENDING_ACADEMICIAN)) {
            roles.remove(Role.ROLE_PENDING_ACADEMICIAN);

            // Eğer başka rolü yoksa (Örn: Sadece başvuru için açıldıysa) STUDENT yapabilirsin veya User'ı silebilirsin.
            // Biz şimdilik rolü siliyoruz.
            user.setRoles(roles);
            userRepository.save(user);
        }

        // İstek tablosundan veriyi sil
        requestRepository.delete(req);

        // (Opsiyonel) Kullanıcıya "Reddedildiniz" maili atılabilir.
        LOGGER.info("Akademisyen başvurusu reddedildi. UserID: {}", userId);
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
