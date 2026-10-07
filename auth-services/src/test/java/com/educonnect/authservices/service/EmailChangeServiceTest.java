package com.educonnect.authservices.service;

import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.EmailVerificationMessage;
import com.educonnect.authservices.dto.message.UserEmailChangedMessage;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRequestRepository studentRequestRepository = mock(StudentRequestRepository.class);
    private final InstitutionPolicy institutionPolicy = mock(InstitutionPolicy.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final OutboxPublisher outboxPublisher = mock(OutboxPublisher.class);
    private final EmailChangeService service = new EmailChangeService(userRepository, studentRequestRepository,
            institutionPolicy, emailVerificationService, refreshTokenService, outboxPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("ayse@ogr.uni.edu.tr", "hash", new HashSet<>(Set.of(Role.ROLE_STUDENT)));
        user.setId(UUID.randomUUID());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(studentRequestRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void theNewAddressMustDifferBeFreeAndMatchTheAccountsDomain() {
        assertCode(() -> service.change(user.getId(), " AYSE@ogr.uni.edu.tr "), "EMAIL_UNCHANGED");
        when(userRepository.findByEmail("dolu@ogr.uni.edu.tr")).thenReturn(Optional.of(new User()));
        assertCode(() -> service.change(user.getId(), "dolu@ogr.uni.edu.tr"), "EMAIL_TAKEN");
        assertCode(() -> service.change(UUID.randomUUID(), "yeni@ogr.uni.edu.tr"), "USER_NOT_FOUND");
        assertThat(user.getEmail()).isEqualTo("ayse@ogr.uni.edu.tr");

        service.change(user.getId(), "yeni@ogr.uni.edu.tr");
        verify(institutionPolicy).requireStudentEmail("yeni@ogr.uni.edu.tr");
        verify(institutionPolicy, never()).requireStaffEmail(anyString());
    }

    @Test
    void theChangeSignsTheUserOutAndTellsTheOldAddress() {
        service.change(user.getId(), "yeni@ogr.uni.edu.tr");

        assertThat(user.getEmail()).isEqualTo("yeni@ogr.uni.edu.tr");
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        verify(emailVerificationService).discardTokens("ayse@ogr.uni.edu.tr");
        verify(refreshTokenService).revokeAllSessions(user.getId());
        verify(outboxPublisher).publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.USER_EMAIL_CHANGED_ROUTING_KEY,
                new UserEmailChangedMessage(user.getId(), "yeni@ogr.uni.edu.tr"));
        verify(outboxPublisher).publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.EMAIL_VERIFICATION_ROUTING_KEY,
                new EmailVerificationMessage("ayse@ogr.uni.edu.tr", null, null, 0, EmailVerificationMessage.EMAIL_CHANGED));
    }

    @Test
    void staffAddressesFollowTheStaffDomain() {
        user.getRoles().clear();
        user.getRoles().add(Role.ROLE_ACADEMICIAN);

        service.change(user.getId(), "hoca@uni.edu.tr");

        verify(institutionPolicy).requireStaffEmail("hoca@uni.edu.tr");
        verify(institutionPolicy, never()).requireStudentEmail(anyString());
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", code);
    }
}
