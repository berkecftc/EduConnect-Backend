package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.EmailVerificationTokenRepository;
import com.educonnect.authservices.Repository.StudentRequestRepository;
import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.EmailVerificationMessage;
import com.educonnect.authservices.models.EmailVerificationToken;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import com.educonnect.authservices.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class EmailVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final StudentRequestRepository studentRequestRepository;
    private final OutboxPublisher outboxPublisher;
    private final AuthSecurityProperties.EmailVerification settings;
    private final AuthSecurityProperties.Links links;
    private final Clock clock;

    @Autowired
    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    StudentRequestRepository studentRequestRepository,
                                    OutboxPublisher outboxPublisher,
                                    AuthSecurityProperties properties) {
        this(tokenRepository, userRepository, studentRequestRepository, outboxPublisher, properties, Clock.systemUTC());
    }

    EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                             UserRepository userRepository,
                             StudentRequestRepository studentRequestRepository,
                             OutboxPublisher outboxPublisher,
                             AuthSecurityProperties properties,
                             Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.outboxPublisher = outboxPublisher;
        this.settings = properties.emailVerification();
        this.links = properties.links();
        this.clock = clock;
    }

    public boolean isRequired() {
        return settings.enabled();
    }

    public Instant verifiedAtForNewAccount() {
        return isRequired() ? null : clock.instant();
    }

    public boolean isVerified(Instant verifiedAt) {
        return !isRequired() || verifiedAt != null;
    }

    @Transactional
    public void sendVerification(String email, String firstName) {
        if (!isRequired() || email == null) {
            return;
        }
        tokenRepository.deleteByEmail(email);
        String rawToken = OpaqueTokens.generate();
        tokenRepository.save(new EmailVerificationToken(OpaqueTokens.hash(rawToken), email,
                clock.instant().plus(settings.tokenTtl())));

        EmailVerificationMessage message = new EmailVerificationMessage(email, firstName,
                links.publicApiBaseUrl() + "/api/auth/verify-email?token=" + rawToken,
                settings.tokenTtl().toHours());
        outboxPublisher.publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.EMAIL_VERIFICATION_ROUTING_KEY, message);
    }

    @Transactional
    public boolean verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        Optional<EmailVerificationToken> token = tokenRepository.findByTokenHash(OpaqueTokens.hash(rawToken));
        if (token.isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        String email = token.get().getEmail();
        if (token.get().isExpired(now)) {
            tokenRepository.delete(token.get());
            return false;
        }
        userRepository.markEmailVerified(email, now);
        studentRequestRepository.markEmailVerified(email, now);
        tokenRepository.deleteByEmail(email);
        LOGGER.info("Email address verified for a pending account");
        return true;
    }

    @Transactional
    public void resend(String email) {
        if (!isRequired() || email == null || email.isBlank()) {
            return;
        }
        Optional<StudentRegistrationRequest> studentRequest = studentRequestRepository.findByEmail(email)
                .filter(request -> request.getEmailVerifiedAt() == null);
        if (studentRequest.isPresent()) {
            sendVerification(email, studentRequest.get().getFirstName());
            return;
        }
        Optional<User> user = userRepository.findByEmail(email).filter(u -> u.getEmailVerifiedAt() == null);
        if (user.isPresent()) {
            sendVerification(email, null);
        }
    }

    public String loginRedirectUrl(boolean verified) {
        return links.frontendBaseUrl() + "/login?emailVerified=" + verified;
    }

    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteExpired(clock.instant());
        if (deleted > 0) {
            LOGGER.info("Cleaned up {} expired email verification tokens", deleted);
        }
    }
}
