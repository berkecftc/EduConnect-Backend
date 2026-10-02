package com.educonnect.authservices.service;

import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.EmailVerificationMessage;
import com.educonnect.authservices.dto.message.UserEmailChangedMessage;
import com.educonnect.authservices.dto.request.EmailChangeRequest;
import com.educonnect.authservices.models.AccountType;
import com.educonnect.authservices.models.EmailChangeToken;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.EmailChangeTokenRepository;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
public class EmailChangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailChangeService.class);

    private final UserRepository userRepository;
    private final StudentRequestRepository studentRequestRepository;
    private final EmailChangeTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final InstitutionPolicy institutionPolicy;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final OutboxPublisher outboxPublisher;
    private final AuthSecurityProperties.EmailVerification settings;
    private final AuthSecurityProperties.Links links;
    private final Clock clock;

    @Autowired
    public EmailChangeService(UserRepository userRepository,
                              StudentRequestRepository studentRequestRepository,
                              EmailChangeTokenRepository tokenRepository,
                              PasswordEncoder passwordEncoder,
                              InstitutionPolicy institutionPolicy,
                              EmailVerificationService emailVerificationService,
                              RefreshTokenService refreshTokenService,
                              OutboxPublisher outboxPublisher,
                              AuthSecurityProperties properties) {
        this(userRepository, studentRequestRepository, tokenRepository, passwordEncoder, institutionPolicy,
                emailVerificationService, refreshTokenService, outboxPublisher, properties, Clock.systemUTC());
    }

    EmailChangeService(UserRepository userRepository,
                       StudentRequestRepository studentRequestRepository,
                       EmailChangeTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       InstitutionPolicy institutionPolicy,
                       EmailVerificationService emailVerificationService,
                       RefreshTokenService refreshTokenService,
                       OutboxPublisher outboxPublisher,
                       AuthSecurityProperties properties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.institutionPolicy = institutionPolicy;
        this.emailVerificationService = emailVerificationService;
        this.refreshTokenService = refreshTokenService;
        this.outboxPublisher = outboxPublisher;
        this.settings = properties.emailVerification();
        this.links = properties.links();
        this.clock = clock;
    }

    @Transactional
    public Result request(String currentEmail, EmailChangeRequest request) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Kullanıcı bulunamadı."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("CURRENT_PASSWORD_INVALID", "Mevcut şifre hatalı.");
        }
        String newEmail = request.newEmail().strip();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("EMAIL_UNCHANGED", "Yeni e-posta mevcut adresinizle aynı.");
        }
        requireAvailable(newEmail);
        AccountType type = AccountType.of(user.getRoles());
        if (type == AccountType.ACADEMICIAN) {
            institutionPolicy.requireStaffEmail(newEmail);
        } else if (type == AccountType.STUDENT) {
            institutionPolicy.requireStudentEmail(newEmail);
        }

        tokenRepository.deleteByUserId(user.getId());
        if (!emailVerificationService.isRequired()) {
            apply(user, newEmail);
            return Result.CHANGED;
        }
        String rawToken = OpaqueTokens.generate();
        tokenRepository.save(new EmailChangeToken(OpaqueTokens.hash(rawToken), user.getId(), newEmail,
                clock.instant().plus(settings.tokenTtl())));
        outboxPublisher.publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.EMAIL_VERIFICATION_ROUTING_KEY,
                new EmailVerificationMessage(newEmail, null,
                        links.publicApiBaseUrl() + "/api/auth/email-change/confirm?token=" + rawToken,
                        settings.tokenTtl().toHours(), EmailVerificationMessage.EMAIL_CHANGE));
        LOGGER.info("E-posta değişikliği için doğrulama bağlantısı gönderildi. UserID: {}", user.getId());
        return Result.VERIFICATION_SENT;
    }

    @Transactional
    public boolean confirm(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        Optional<EmailChangeToken> token = tokenRepository.findByTokenHash(OpaqueTokens.hash(rawToken));
        if (token.isEmpty()) {
            return false;
        }
        tokenRepository.delete(token.get());
        if (token.get().isExpired(clock.instant())) {
            return false;
        }
        Optional<User> user = userRepository.findById(token.get().getUserId());
        if (user.isEmpty() || userRepository.findByEmail(token.get().getNewEmail()).isPresent()
                || studentRequestRepository.findByEmail(token.get().getNewEmail()).isPresent()) {
            return false;
        }
        apply(user.get(), token.get().getNewEmail());
        return true;
    }

    public String loginRedirectUrl(boolean changed) {
        return links.frontendBaseUrl() + "/login?emailChanged=" + changed;
    }

    @Scheduled(cron = "0 40 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteExpired(clock.instant());
        if (deleted > 0) {
            LOGGER.info("Cleaned up {} expired email change tokens", deleted);
        }
    }

    private void requireAvailable(String email) {
        if (userRepository.findByEmail(email).isPresent() || studentRequestRepository.findByEmail(email).isPresent()
                || tokenRepository.existsByNewEmail(email)) {
            throw new ConflictException("EMAIL_TAKEN", "Bu e-posta adresi başka bir hesapta veya başvuruda kullanılıyor.");
        }
    }

    private void apply(User user, String newEmail) {
        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        user.setEmailVerifiedAt(clock.instant());
        userRepository.save(user);
        emailVerificationService.discardTokens(oldEmail);
        refreshTokenService.revokeAllSessions(user.getId());
        outboxPublisher.publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.USER_EMAIL_CHANGED_ROUTING_KEY,
                new UserEmailChangedMessage(user.getId(), newEmail));
        outboxPublisher.publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.EMAIL_VERIFICATION_ROUTING_KEY,
                new EmailVerificationMessage(oldEmail, null, null, 0, EmailVerificationMessage.EMAIL_CHANGED));
        LOGGER.info("E-posta adresi değiştirildi; oturumlar kapatıldı. UserID: {}", user.getId());
    }

    public enum Result {
        CHANGED,
        VERIFICATION_SENT
    }
}
