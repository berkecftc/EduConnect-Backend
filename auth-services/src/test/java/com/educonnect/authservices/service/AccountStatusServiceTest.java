package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.UserAccountStatusMessage;
import com.educonnect.authservices.models.AccountStatus;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final String ADMIN_EMAIL = "admin@example.edu";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private OutboxPublisher outboxPublisher;

    private AccountStatusService service;
    private final UUID userId = UUID.randomUUID();
    private User student;

    @BeforeEach
    void setUp() {
        service = new AccountStatusService(userRepository, refreshTokenService, outboxPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        student = new User("ayse@example.edu", "hash", new HashSet<>(Set.of(Role.ROLE_STUDENT)));
        student.setId(userId);
    }

    @Test
    void suspend_shouldBlockAccountRevokeSessionsAndNotifyUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));

        service.suspend(userId, ADMIN_EMAIL, "  Kural ihlali  ");

        assertThat(student.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(student.getStatusReason()).isEqualTo("Kural ihlali");
        assertThat(student.getStatusChangedAt()).isEqualTo(NOW);
        verify(userRepository).save(student);
        verify(refreshTokenService).revokeAllSessions(userId);
        ArgumentCaptor<UserAccountStatusMessage> message = ArgumentCaptor.forClass(UserAccountStatusMessage.class);
        verify(outboxPublisher).publish(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY), message.capture());
        assertThat(message.getValue().getStatus()).isEqualTo(AccountStatusService.STATUS_SUSPENDED);
        assertThat(message.getValue().getRejectionReason()).isEqualTo("Kural ihlali");
    }

    @Test
    void suspend_ownAccount_shouldFail() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.suspend(userId, "AYSE@example.edu", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void suspend_adminAccount_shouldFailWithConflict() {
        User admin = new User("other-admin@example.edu", "hash", new HashSet<>(Set.of(Role.ROLE_ADMIN)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.suspend(userId, ADMIN_EMAIL, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void suspend_alreadySuspended_shouldBeNoOp() {
        student.suspend("eski", NOW.minusSeconds(100));
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));

        service.suspend(userId, ADMIN_EMAIL, "yeni");

        assertThat(student.getStatusReason()).isEqualTo("eski");
        verifyNoInteractions(refreshTokenService, outboxPublisher);
    }

    @Test
    void reactivate_shouldRestoreAccessAndNotify() {
        student.suspend("eski", NOW.minusSeconds(100));
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));

        service.reactivate(userId, ADMIN_EMAIL);

        assertThat(student.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(student.getStatusReason()).isNull();
        ArgumentCaptor<UserAccountStatusMessage> message = ArgumentCaptor.forClass(UserAccountStatusMessage.class);
        verify(outboxPublisher).publish(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY), message.capture());
        assertThat(message.getValue().getStatus()).isEqualTo(AccountStatusService.STATUS_REACTIVATED);
    }
}
