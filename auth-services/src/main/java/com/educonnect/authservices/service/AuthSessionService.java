package com.educonnect.authservices.service;

import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.GamificationActionType;
import com.educonnect.authservices.dto.message.GamificationEventMessage;
import com.educonnect.authservices.dto.request.LoginRequest;
import com.educonnect.authservices.dto.response.AuthResponse;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
public class AuthSessionService {

    private final UserRepository userRepository;
    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;
    private final OutboxPublisher outboxPublisher;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;

    public AuthSessionService(UserRepository userRepository,
                              JWTService jwtService,
                              AuthenticationManager authenticationManager,
                              OutboxPublisher outboxPublisher,
                              RefreshTokenService refreshTokenService,
                              LoginAttemptService loginAttemptService,
                              EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.outboxPublisher = outboxPublisher;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.emailVerificationService = emailVerificationService;
    }

    public AuthResponse login(LoginRequest loginRequest) {
        loginAttemptService.ensureNotLocked(loginRequest.getEmail());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(loginRequest.getEmail());
            throw e;
        }

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        if (user.getRoles().contains(Role.ROLE_PENDING_ACADEMICIAN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Hesabınız henüz onaylanmadı. Lütfen yönetici onayını bekleyin.");
        }
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Hesabınız askıya alınmıştır. Ayrıntılı bilgi için yönetici ile iletişime geçin.");
        }
        if (!emailVerificationService.isVerified(user.getEmailVerifiedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "E-posta adresinizi doğrulamanız gerekiyor. Gelen kutunuzdaki doğrulama bağlantısını kullanın.");
        }

        loginAttemptService.recordSuccess(user.getId());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());

        LocalDate istanbulToday = LocalDate.now(ZoneId.of("Europe/Istanbul"));
        String referenceId = "LOGIN:" + istanbulToday + ":" + user.getId();
        GamificationEventMessage gamificationEvent = new GamificationEventMessage(
                user.getId(),
                GamificationActionType.DAILY_LOGIN,
                referenceId,
                OffsetDateTime.now(ZoneId.of("Europe/Istanbul"))
        );
        if (user.getRoles().contains(Role.ROLE_STUDENT)) {
            outboxPublisher.publish(
                    RabbitMQConfig.GAMIFICATION_EXCHANGE,
                    RabbitMQConfig.GAMIFICATION_USER_LOGIN_ROUTING_KEY,
                    gamificationEvent
            );
        }

        return AuthResponses.of(jwt, refreshToken, "Login successful", user);
    }

    public AuthResponse refreshAccessToken(String refreshTokenStr) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(refreshTokenStr);

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new RefreshTokenService.InvalidRefreshTokenException("Invalid refresh token"));
        if (user.isSuspended()) {
            refreshTokenService.revokeAllSessions(user.getId());
            throw new RefreshTokenService.InvalidRefreshTokenException("Account is suspended");
        }

        String newAccessToken = jwtService.generateToken(user);
        return AuthResponses.of(newAccessToken, rotated.rawToken(), "Token refreshed successfully", user);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revokeSession(refreshToken);
    }
}
