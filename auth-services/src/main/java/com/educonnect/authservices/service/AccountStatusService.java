package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.UserAccountStatusMessage;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AccountStatusService {

    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String STATUS_REACTIVATED = "REACTIVATED";

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountStatusService.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    @Autowired
    public AccountStatusService(UserRepository userRepository,
                                RefreshTokenService refreshTokenService,
                                OutboxPublisher outboxPublisher) {
        this(userRepository, refreshTokenService, outboxPublisher, Clock.systemUTC());
    }

    AccountStatusService(UserRepository userRepository,
                         RefreshTokenService refreshTokenService,
                         OutboxPublisher outboxPublisher,
                         Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.outboxPublisher = outboxPublisher;
        this.clock = clock;
    }

    @Transactional
    public void suspend(UUID userId, String actorEmail, String reason) {
        User user = findUser(userId);
        if (user.getEmail().equalsIgnoreCase(actorEmail)) {
            throw new IllegalArgumentException("Kendi hesabınızı askıya alamazsınız.");
        }
        if (user.getRoles() != null && user.getRoles().contains(Role.ROLE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Admin hesapları askıya alınamaz. Önce admin yetkisini kaldırın.");
        }
        if (user.isSuspended()) {
            return;
        }
        String normalizedReason = normalizeReason(reason);
        user.suspend(normalizedReason, clock.instant());
        userRepository.save(user);
        refreshTokenService.revokeAllSessions(userId);
        LOGGER.warn("AUDIT account suspended: userId={}, by={}", userId, actorEmail);
        notifyUser(user, STATUS_SUSPENDED, normalizedReason);
    }

    @Transactional
    public void reactivate(UUID userId, String actorEmail) {
        User user = findUser(userId);
        if (!user.isSuspended()) {
            return;
        }
        user.reactivate(clock.instant());
        userRepository.save(user);
        LOGGER.warn("AUDIT account reactivated: userId={}, by={}", userId, actorEmail);
        notifyUser(user, STATUS_REACTIVATED, null);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Kullanıcı bulunamadı"));
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Askıya alma gerekçesi en fazla " + MAX_REASON_LENGTH + " karakter olabilir.");
        }
        return trimmed;
    }

    private void notifyUser(User user, String status, String reason) {
        String userType = user.getRoles() != null && user.getRoles().contains(Role.ROLE_ACADEMICIAN) ? "ACADEMICIAN" : "STUDENT";
        UserAccountStatusMessage message = new UserAccountStatusMessage(user.getEmail(), null, null, status, userType, reason);
        outboxPublisher.publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY, message);
    }
}
