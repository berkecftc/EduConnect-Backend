package com.educonnect.authservices.service;

import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.EmailVerificationMessage;
import com.educonnect.authservices.dto.message.UserEmailChangedMessage;
import com.educonnect.authservices.models.AccountType;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class EmailChangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailChangeService.class);

    private final UserRepository userRepository;
    private final StudentRequestRepository studentRequestRepository;
    private final InstitutionPolicy institutionPolicy;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    @Autowired
    public EmailChangeService(UserRepository userRepository,
                              StudentRequestRepository studentRequestRepository,
                              InstitutionPolicy institutionPolicy,
                              EmailVerificationService emailVerificationService,
                              RefreshTokenService refreshTokenService,
                              OutboxPublisher outboxPublisher) {
        this(userRepository, studentRequestRepository, institutionPolicy, emailVerificationService, refreshTokenService,
                outboxPublisher, Clock.systemUTC());
    }

    EmailChangeService(UserRepository userRepository,
                       StudentRequestRepository studentRequestRepository,
                       InstitutionPolicy institutionPolicy,
                       EmailVerificationService emailVerificationService,
                       RefreshTokenService refreshTokenService,
                       OutboxPublisher outboxPublisher,
                       Clock clock) {
        this.userRepository = userRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.institutionPolicy = institutionPolicy;
        this.emailVerificationService = emailVerificationService;
        this.refreshTokenService = refreshTokenService;
        this.outboxPublisher = outboxPublisher;
        this.clock = clock;
    }

    @Transactional
    public User change(UUID userId, String requestedEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Kullanıcı bulunamadı."));
        String newEmail = requestedEmail.strip();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("EMAIL_UNCHANGED", "Yeni e-posta mevcut adresle aynı.");
        }
        if (userRepository.findByEmail(newEmail).isPresent() || studentRequestRepository.findByEmail(newEmail).isPresent()) {
            throw new ConflictException("EMAIL_TAKEN", "Bu e-posta adresi başka bir hesapta veya başvuruda kullanılıyor.");
        }
        AccountType type = AccountType.of(user.getRoles());
        if (type == AccountType.ACADEMICIAN) {
            institutionPolicy.requireStaffEmail(newEmail);
        } else if (type == AccountType.STUDENT) {
            institutionPolicy.requireStudentEmail(newEmail);
        }

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
        LOGGER.info("E-posta adresi yönetim tarafından değiştirildi; oturumlar kapatıldı. UserID: {}", user.getId());
        return user;
    }
}
