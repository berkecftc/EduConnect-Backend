package com.educonnect.authservices.service;

import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.PasswordResetMessage;
import com.educonnect.authservices.dto.request.ChangePasswordRequest;
import com.educonnect.authservices.dto.request.ForgotPasswordRequest;
import com.educonnect.authservices.dto.request.ResetPasswordRequest;
import com.educonnect.authservices.models.PasswordResetToken;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.PasswordResetTokenRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.NotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;

@Service
public class PasswordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxPublisher outboxPublisher;
    private final RefreshTokenService refreshTokenService;
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptService loginAttemptService;
    private final AuthSecurityProperties.Links links;

    public PasswordService(UserRepository userRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           PasswordEncoder passwordEncoder,
                           OutboxPublisher outboxPublisher,
                           RefreshTokenService refreshTokenService,
                           PasswordPolicy passwordPolicy,
                           LoginAttemptService loginAttemptService,
                           AuthSecurityProperties authSecurityProperties) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxPublisher = outboxPublisher;
        this.refreshTokenService = refreshTokenService;
        this.passwordPolicy = passwordPolicy;
        this.loginAttemptService = loginAttemptService;
        this.links = authSecurityProperties.links();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, UserDetails authenticatedUser) {
        User user = userRepository.findByEmail(authenticatedUser.getUsername())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("WRONG_CURRENT_PASSWORD", "Wrong current password");
        }

        if (!request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new BadRequestException("PASSWORD_CONFIRMATION_MISMATCH", "New password and confirmation do not match");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("PASSWORD_UNCHANGED", "New password cannot be the same as the old password");
        }
        passwordPolicy.validateNewPassword(request.getNewPassword(), user.getEmail());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllSessions(user.getId());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Bu email adresi ile kayıtlı kullanıcı bulunamadı."));

        passwordResetTokenRepository.deleteByUserId(user.getId());

        String token = OpaqueTokens.generate();

        PasswordResetToken resetToken = new PasswordResetToken(
                OpaqueTokens.hash(token),
                user.getId(),
                Instant.now().plusSeconds(15 * 60)
        );
        passwordResetTokenRepository.save(resetToken);

        String resetLink = links.frontendBaseUrl() + "/reset-password?token=" + token;

        PasswordResetMessage message = new PasswordResetMessage(
                user.getEmail(),
                null,
                null,
                token,
                resetLink
        );

        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.PASSWORD_RESET_ROUTING_KEY,
                message
        );

        LOGGER.info("Şifre sıfırlama e-postası kuyruğa alındı. UserID: {}", user.getId());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String token = request.getToken();
        String newPassword = request.getNewPassword();
        String confirmPassword = request.getConfirmPassword();

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token gereklidir.");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("PASSWORD_CONFIRMATION_MISMATCH", "Şifreler eşleşmiyor.");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(OpaqueTokens.hash(token))
                .orElseThrow(() -> new NoSuchElementException("Geçersiz veya süresi dolmuş token."));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new BadRequestException("RESET_TOKEN_EXPIRED", "Token süresi dolmuş. Lütfen yeni bir şifre sıfırlama talebi oluşturun.");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Kullanıcı bulunamadı."));
        passwordPolicy.validateResetPassword(newPassword, user.getEmail());

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllSessions(user.getId());
        loginAttemptService.recordSuccess(user.getId());

        passwordResetTokenRepository.delete(resetToken);

        LOGGER.info("Şifre başarıyla sıfırlandı. UserID: {}", user.getId());
    }
}
