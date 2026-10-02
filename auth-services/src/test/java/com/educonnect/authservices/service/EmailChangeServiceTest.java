package com.educonnect.authservices.service;

import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.EmailVerificationMessage;
import com.educonnect.authservices.dto.message.UserEmailChangedMessage;
import com.educonnect.authservices.dto.request.EmailChangeRequest;
import com.educonnect.authservices.models.EmailChangeToken;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.EmailChangeTokenRepository;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRequestRepository studentRequestRepository = mock(StudentRequestRepository.class);
    private final EmailChangeTokenRepository tokenRepository = mock(EmailChangeTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final InstitutionPolicy institutionPolicy = mock(InstitutionPolicy.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final OutboxPublisher outboxPublisher = mock(OutboxPublisher.class);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("ayse@ogr.uni.edu.tr", "hash", new HashSet<>(Set.of(Role.ROLE_STUDENT)));
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmail("ayse@ogr.uni.edu.tr")).thenReturn(Optional.of(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("dogru", "hash")).thenReturn(true);
        when(studentRequestRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    private EmailChangeService service(boolean verificationRequired) {
        when(emailVerificationService.isRequired()).thenReturn(verificationRequired);
        AuthSecurityProperties properties = new AuthSecurityProperties(null, null, null,
                new AuthSecurityProperties.EmailVerification(verificationRequired, Duration.ofHours(24)),
                new AuthSecurityProperties.Links("https://app.example.edu"),
                null, null);
        return new EmailChangeService(userRepository, studentRequestRepository, tokenRepository, passwordEncoder,
                institutionPolicy, emailVerificationService, refreshTokenService, outboxPublisher, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theRequestNeedsThePasswordANewFreeAddressAndTheRightDomain() {
        EmailChangeService service = service(true);
        assertCode(() -> service.request(user.getEmail(), new EmailChangeRequest("yeni@ogr.uni.edu.tr", "yanlis")),
                "CURRENT_PASSWORD_INVALID");
        assertCode(() -> service.request(user.getEmail(), new EmailChangeRequest(" AYSE@ogr.uni.edu.tr ", "dogru")),
                "EMAIL_UNCHANGED");
        when(userRepository.findByEmail("dolu@ogr.uni.edu.tr")).thenReturn(Optional.of(new User()));
        assertCode(() -> service.request(user.getEmail(), new EmailChangeRequest("dolu@ogr.uni.edu.tr", "dogru")),
                "EMAIL_TAKEN");

        service.request(user.getEmail(), new EmailChangeRequest("yeni@ogr.uni.edu.tr", "dogru"));
        verify(institutionPolicy).requireStudentEmail("yeni@ogr.uni.edu.tr");
        verify(institutionPolicy, never()).requireStaffEmail(anyString());
    }

    @Test
    void theNewAddressMustBeConfirmedBeforeTheAccountChanges() {
        EmailChangeService service = service(true);

        assertThat(service.request(user.getEmail(), new EmailChangeRequest("yeni@ogr.uni.edu.tr", "dogru")))
                .isEqualTo(EmailChangeService.Result.VERIFICATION_SENT);
        ArgumentCaptor<EmailVerificationMessage> sent = ArgumentCaptor.forClass(EmailVerificationMessage.class);
        verify(outboxPublisher).publish(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.EMAIL_VERIFICATION_ROUTING_KEY), sent.capture());
        assertThat(sent.getValue().email()).isEqualTo("yeni@ogr.uni.edu.tr");
        assertThat(sent.getValue().purpose()).isEqualTo(EmailVerificationMessage.EMAIL_CHANGE);
        assertThat(sent.getValue().verificationLink()).startsWith("https://app.example.edu/email-change/confirm?token=");
        assertThat(user.getEmail()).isEqualTo("ayse@ogr.uni.edu.tr");

        String rawToken = sent.getValue().verificationLink().substring(sent.getValue().verificationLink().indexOf("token=") + 6);
        ArgumentCaptor<EmailChangeToken> stored = ArgumentCaptor.forClass(EmailChangeToken.class);
        verify(tokenRepository).save(stored.capture());
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash(rawToken))).thenReturn(Optional.of(stored.getValue()));

        assertThat(service.confirm("baska")).isFalse();
        assertThat(service.confirm(rawToken)).isTrue();
        assertThat(user.getEmail()).isEqualTo("yeni@ogr.uni.edu.tr");
        verify(refreshTokenService).revokeAllSessions(user.getId());
        verify(outboxPublisher).publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.USER_EMAIL_CHANGED_ROUTING_KEY,
                new UserEmailChangedMessage(user.getId(), "yeni@ogr.uni.edu.tr"));
        verify(outboxPublisher).publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.EMAIL_VERIFICATION_ROUTING_KEY,
                new EmailVerificationMessage("ayse@ogr.uni.edu.tr", null, null, 0, EmailVerificationMessage.EMAIL_CHANGED));
    }

    @Test
    void anExpiredLinkChangesNothing() {
        EmailChangeService service = service(true);
        EmailChangeToken expired = new EmailChangeToken(OpaqueTokens.hash("eski"), user.getId(), "yeni@ogr.uni.edu.tr",
                NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("eski"))).thenReturn(Optional.of(expired));

        assertThat(service.confirm("eski")).isFalse();
        assertThat(user.getEmail()).isEqualTo("ayse@ogr.uni.edu.tr");
        verify(tokenRepository).delete(expired);
    }

    @Test
    void withoutEmailVerificationTheChangeAppliesAtOnce() {
        user.getRoles().add(Role.ROLE_ACADEMICIAN);
        EmailChangeService service = service(false);

        assertThat(service.request(user.getEmail(), new EmailChangeRequest("hoca@uni.edu.tr", "dogru")))
                .isEqualTo(EmailChangeService.Result.CHANGED);
        assertThat(user.getEmail()).isEqualTo("hoca@uni.edu.tr");
        verify(institutionPolicy).requireStaffEmail("hoca@uni.edu.tr");
        verify(tokenRepository, never()).save(any());
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", code);
    }
}
