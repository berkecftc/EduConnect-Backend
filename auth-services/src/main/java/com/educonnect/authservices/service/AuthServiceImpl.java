package com.educonnect.authservices.service;

import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.AcademicianProfileMessage;
import com.educonnect.authservices.dto.message.UserRegisteredMessage;
import com.educonnect.authservices.dto.request.ChangePasswordRequest;
import com.educonnect.authservices.dto.request.LoginRequest;
import com.educonnect.authservices.dto.request.RegisterRequest;
import com.educonnect.authservices.dto.response.AuthResponse;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.Repository.UserRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;

    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JWTService jwtService,
                           AuthenticationManager authenticationManager,
                           RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.rabbitTemplate = rabbitTemplate;
    }

    public AuthResponse register(RegisterRequest request) {
        // Basit alan kontrolleri
        if (request.getEmail() == null || request.getPassword() == null
                || request.getFirstName() == null || request.getLastName() == null) {
            throw new IllegalArgumentException("Missing required fields for registration");
        }

        // Varsayılan olarak her yeni kullanıcı STUDENT rolüyle başlar
        Set<Role> roles = Stream.of(Role.ROLE_STUDENT)
                .collect(Collectors.toSet());

        var user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                roles
        );

        User savedUser = userRepository.save(user);


        Set<String> roleStrings = roles.stream()
                .map(Role::name)
                .collect(Collectors.toSet());

        // Ogrenciye ozel alanlari da doldur
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
        LOGGER.info("Published registration message for userId={} to exchange={} rk={} (dept={}, studentId={})",
                savedUser.getId(), RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY,
                request.getDepartment(), request.getStudentId());

        var jwtToken = jwtService.generateToken(savedUser);

        return new AuthResponse(jwtToken, "User registered successfully. Profile creation initiated.");
    }

    public AuthResponse registerStudent(RegisterRequest request) {
        // Basit validasyonlar
        if (request.getEmail() == null || request.getPassword() == null
                || request.getFirstName() == null || request.getLastName() == null
                || request.getStudentId() == null || request.getDepartment() == null) {
            throw new IllegalArgumentException("Missing required fields for student registration");
        }

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
        LOGGER.info("Published student registration message for userId={} to exchange={} rk={} (dept={}, studentId={})",
                savedUser.getId(), RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY,
                request.getDepartment(), request.getStudentId());

        var jwtToken = jwtService.generateToken(savedUser);
        return new AuthResponse(jwtToken, "Student registered successfully. Profile creation initiated.");
    }

    // requestAcademicianAccount metodunu GÜNCELLE
    public void requestAcademicianAccount(RegisterRequest request) {
        // ... (Mevcut 'email already registered' kontrolü burada olmalı) ...

        // 1. Kullanıcıyı 'PENDING' rolüyle kaydet
        Set<Role> roles = Stream.of(Role.ROLE_PENDING_ACADEMICIAN)
                .collect(Collectors.toSet());

        var user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                roles
        );

        User savedUser = userRepository.save(user); // Kullanıcıyı kaydet ve ID'sini al

        // 2. RabbitMQ mesajını oluştur (TÜM detaylarla)
        AcademicianProfileMessage profileMessage = new AcademicianProfileMessage(
                savedUser.getId(),
                request.getFirstName(),
                request.getLastName(),
                request.getTitle(),
                request.getDepartment(),
                request.getOfficeNumber()
        );

        // 3. user-service'in dinleyeceği YENİ bir kuyruğa/routing-key'e gönder
        // (Bu, 'ROLE_STUDENT' için kullandığımız 'user-profile-creation-queue'dan FARKLI olmalı)
        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ACADEMICIAN_ROUTING_KEY, profileMessage);

        System.out.println("Academician registration request saved and profile creation message sent.");
    }

    public void approveAcademician(UUID userId) {
        // 1. Onay bekleyen kullanıcıyı bul
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        // 2. Rolünü güncelle
        Set<Role> roles = user.getRoles();
        roles.remove(Role.ROLE_PENDING_ACADEMICIAN); // Eski rolü sil
        roles.add(Role.ROLE_ACADEMICIAN); // Gerçek rolü ekle
        user.setRoles(roles);

        userRepository.save(user); // Kullanıcıyı güncelle

        // 3. ŞİMDİ RABBITMQ MESAJINI GÖNDER!
        // Profilin oluşturulma zamanı geldi.
        // (RegisterRequest'teki firstName, lastName bilgisi burada yok.
        // Bu bilgiyi ya 'user' tablosunda geçici tutmalı ya da
        // Admin'den onaylarken almalısınız. Şimdilik e-postayı kullanalım)


        // (Basitlik adına, 'firstName' ve 'lastName' şimdilik e-postanın parçaları olsun)
        String firstName = user.getEmail().split("@")[0];
        String lastName = "Academician";

        sendMessageToUserQueue(user, firstName, lastName, roles);
    }

    private void sendMessageToUserQueue(User user, String firstName, String lastName, Set<Role> roles) {
        Set<String> roleStrings = roles.stream()
                .map(Role::name)
                .collect(Collectors.toSet());

        UserRegisteredMessage message = new UserRegisteredMessage(
                user.getId(),
                firstName,
                lastName,
                roleStrings
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );
    }


    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NoSuchElementException("User not found after authentication"));

        var jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken, "Login successful");
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
}
