package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.RefreshTokenRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.models.RefreshToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JWTService jwtService;
    private final AuthSecurityProperties properties;
    private final Clock clock;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               JWTService jwtService,
                               AuthSecurityProperties properties) {
        this(refreshTokenRepository, jwtService, properties, Clock.systemUTC());
    }

    RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                        JWTService jwtService,
                        AuthSecurityProperties properties,
                        Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String issue(UUID userId) {
        String rawToken = store(userId, UUID.randomUUID());
        enforceActiveLimit(userId);
        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is required");
        }
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(OpaqueTokens.hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        Instant now = clock.instant();

        if (current.isRevoked()) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            LOGGER.warn("Refresh token reuse detected; session family {} of user {} revoked",
                    current.getFamilyId(), current.getUserId());
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            throw new InvalidRefreshTokenException("Refresh token was expired. Please make a new signin request");
        }

        current.revoke(now);
        String nextToken = store(current.getUserId(), current.getFamilyId());
        return new RotatedRefreshToken(current.getUserId(), nextToken);
    }

    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, clock.instant());
        if (revoked > 0) {
            LOGGER.info("Revoked {} refresh tokens of user {}", revoked, userId);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        int deletedCount = refreshTokenRepository.deleteByExpiryDateBefore(clock.instant());
        if (deletedCount > 0) {
            LOGGER.info("Cleaned up {} expired refresh tokens", deletedCount);
        }
    }

    private String store(UUID userId, UUID familyId) {
        String rawToken = OpaqueTokens.generate();
        Instant expiry = clock.instant().plusMillis(jwtService.getRefreshTokenExpirationMs());
        refreshTokenRepository.save(new RefreshToken(OpaqueTokens.hash(rawToken), familyId, userId, expiry));
        return rawToken;
    }

    private void enforceActiveLimit(UUID userId) {
        Instant now = clock.instant();
        List<RefreshToken> active = refreshTokenRepository.findActiveByUserId(userId, now);
        int limit = properties.refreshTokens().maxActivePerUser();
        active.stream()
                .skip(limit)
                .map(RefreshToken::getFamilyId)
                .distinct()
                .forEach(familyId -> refreshTokenRepository.revokeFamily(familyId, now));
    }

    public record RotatedRefreshToken(UUID userId, String rawToken) {
    }

    public static class InvalidRefreshTokenException extends RuntimeException {

        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
}
