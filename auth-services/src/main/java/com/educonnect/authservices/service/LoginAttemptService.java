package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
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
        if (!settings.enabled() || email == null) {
            return;
        }
        if (userRepository.isLoginLocked(email, clock.instant())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Çok fazla hatalı giriş denemesi yapıldı. Lütfen " + settings.lockDuration().toMinutes()
                            + " dakika sonra tekrar deneyin.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {
        if (!settings.enabled() || email == null) {
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
