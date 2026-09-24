package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final String EMAIL = "ayse@example.edu";

    @Mock
    private UserRepository userRepository;

    private LoginAttemptService service(boolean enabled) {
        AuthSecurityProperties properties = new AuthSecurityProperties(null,
                new AuthSecurityProperties.LoginProtection(enabled, 5, Duration.ofMinutes(15)), null);
        return new LoginAttemptService(userRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void disabled_shouldNeverTouchRepository() {
        LoginAttemptService service = service(false);

        service.ensureNotLocked(EMAIL);
        service.recordFailure(EMAIL);
        service.recordSuccess(UUID.randomUUID());

        verifyNoInteractions(userRepository);
    }

    @Test
    void ensureNotLocked_whenAccountLocked_shouldReturnTooManyRequests() {
        when(userRepository.isLoginLocked(EMAIL, NOW)).thenReturn(true);

        assertThatThrownBy(() -> service(true).ensureNotLocked(EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");
    }

    @Test
    void ensureNotLocked_whenAccountOpen_shouldPass() {
        when(userRepository.isLoginLocked(EMAIL, NOW)).thenReturn(false);

        assertThatCode(() -> service(true).ensureNotLocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void recordFailure_shouldIncrementAndLockAfterThreshold() {
        when(userRepository.incrementFailedLoginAttempts(EMAIL)).thenReturn(1);

        service(true).recordFailure(EMAIL);

        verify(userRepository).lockIfAttemptsExceeded(EMAIL, 5, NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void recordFailure_forUnknownEmail_shouldNotTryToLock() {
        when(userRepository.incrementFailedLoginAttempts(EMAIL)).thenReturn(0);

        service(true).recordFailure(EMAIL);

        verify(userRepository, never()).lockIfAttemptsExceeded(anyString(), anyInt(), any());
    }

    @Test
    void recordSuccess_shouldResetCounter() {
        UUID userId = UUID.randomUUID();

        service(true).recordSuccess(userId);

        verify(userRepository).resetFailedLoginAttempts(userId);
    }
}
