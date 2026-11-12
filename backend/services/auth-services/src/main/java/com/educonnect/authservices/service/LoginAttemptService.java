package com.educonnect.authservices.service;

import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginAttemptService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final AuthSecurityProperties.LoginProtection settings;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(UserRepository userRepository, AuthSecurityProperties properties) {
        this(userRepository, properties, Clock.systemUTC());
    }

    LoginAttemptService(UserRepository userRepository, AuthSecurityProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.settings = properties.loginProtection();
        this.clock = clock;
    }

    public void ensureNotLocked(String email) {
        if (!settings.enabled()) {
            return;
        }
        Instant now = clock.instant();
        Optional<Instant> lockedUntil = userRepository.findLoginLockedUntil(email, now);
        if (lockedUntil.isPresent()) {
            long seconds = Math.max(1, Duration.between(now, lockedUntil.get()).toSeconds());
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                    "Çok fazla hatalı giriş denemesi yapıldı. Lütfen " + Math.ceilDiv(seconds, 60)
                            + " dakika sonra tekrar deneyin.", headers);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {
        if (!settings.enabled()) {
            return;
        }
        if (userRepository.incrementFailedLoginAttempts(email) == 0) {
            return;
        }
        int locked = userRepository.lockIfAttemptsExceeded(email, settings.maxAttempts(),
                clock.instant().plus(settings.lockDuration()));
        if (locked > 0) {
            LOGGER.warn("Account temporarily locked after {} failed login attempts", settings.maxAttempts());
        }
    }

    @Transactional
    public void recordSuccess(UUID userId) {
        if (!settings.enabled()) {
            return;
        }
        userRepository.resetFailedLoginAttempts(userId);
    }
}
