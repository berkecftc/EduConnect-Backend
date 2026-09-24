package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.EmailVerificationTokenRepository;
import com.educonnect.authservices.Repository.StudentRequestRepository;
import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.EmailVerificationMessage;
import com.educonnect.authservices.models.EmailVerificationToken;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final String EMAIL = "ayse@example.edu";

    @Mock
    private EmailVerificationTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRequestRepository studentRequestRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private EmailVerificationService service(boolean enabled) {
        AuthSecurityProperties properties = new AuthSecurityProperties(null, null, null,
                new AuthSecurityProperties.EmailVerification(enabled, Duration.ofHours(24)),
                new AuthSecurityProperties.Links("https://app.example.edu/", "https://api.example.edu"),
                null);
        return new EmailVerificationService(tokenRepository, userRepository, studentRequestRepository, rabbitTemplate,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void disabled_shouldTreatNewAccountsAsVerifiedAndSendNothing() {
        EmailVerificationService service = service(false);

        service.sendVerification(EMAIL, "Ayşe");

        assertThat(service.verifiedAtForNewAccount()).isEqualTo(NOW);
        assertThat(service.isVerified(null)).isTrue();
        verifyNoInteractions(tokenRepository, rabbitTemplate);
    }

    @Test
    void enabled_newAccountsStartUnverified() {
        EmailVerificationService service = service(true);

        assertThat(service.verifiedAtForNewAccount()).isNull();
        assertThat(service.isVerified(null)).isFalse();
        assertThat(service.isVerified(NOW)).isTrue();
    }

    @Test
    void sendVerification_shouldStoreHashAndSendLinkWithRawToken() {
        service(true).sendVerification(EMAIL, "Ayşe");

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).deleteByEmail(EMAIL);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<EmailVerificationMessage> sent = ArgumentCaptor.forClass(EmailVerificationMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.EMAIL_VERIFICATION_ROUTING_KEY), sent.capture());

        String link = sent.getValue().verificationLink();
        assertThat(link).startsWith("https://api.example.edu/api/auth/verify-email?token=");
        String rawToken = link.substring(link.indexOf("token=") + 6);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(rawToken)).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(sent.getValue().validHours()).isEqualTo(24);
    }

    @Test
    void verify_withValidToken_shouldMarkUserAndStudentRequestVerified() {
        EmailVerificationToken token = new EmailVerificationToken(OpaqueTokens.hash("raw"), EMAIL, NOW.plusSeconds(60));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("raw"))).thenReturn(Optional.of(token));

        assertThat(service(true).verify("raw")).isTrue();

        verify(userRepository).markEmailVerified(EMAIL, NOW);
        verify(studentRequestRepository).markEmailVerified(EMAIL, NOW);
        verify(tokenRepository).deleteByEmail(EMAIL);
    }

    @Test
    void verify_withExpiredToken_shouldFailAndDeleteToken() {
        EmailVerificationToken token = new EmailVerificationToken(OpaqueTokens.hash("raw"), EMAIL, NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash("raw"))).thenReturn(Optional.of(token));

        assertThat(service(true).verify("raw")).isFalse();

        verify(tokenRepository).delete(token);
        verify(userRepository, never()).markEmailVerified(anyString(), any());
    }

    @Test
    void verify_withUnknownOrMissingToken_shouldFail() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service(true).verify("unknown")).isFalse();
        assertThat(service(true).verify(null)).isFalse();
    }

    @Test
    void resend_forPendingUnverifiedStudentRequest_shouldSendNewLink() {
        StudentRegistrationRequest request = new StudentRegistrationRequest();
        request.setEmail(EMAIL);
        request.setFirstName("Ayşe");
        when(studentRequestRepository.findByEmail(EMAIL)).thenReturn(Optional.of(request));

        service(true).resend(EMAIL);

        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.EMAIL_VERIFICATION_ROUTING_KEY), any(EmailVerificationMessage.class));
    }

    @Test
    void resend_forUnknownEmail_shouldSendNothing() {
        when(studentRequestRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service(true).resend(EMAIL);

        verifyNoInteractions(rabbitTemplate);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void loginRedirectUrl_shouldPointToFrontendLogin() {
        assertThat(service(true).loginRedirectUrl(true)).isEqualTo("https://app.example.edu/login?emailVerified=true");
    }
}
