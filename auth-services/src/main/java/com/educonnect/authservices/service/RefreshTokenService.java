package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.RefreshTokenRepository;
import com.educonnect.authservices.models.RefreshToken;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JWTService jwtService;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JWTService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    /**
     * Yeni refresh token oluşturur ve veritabanına kaydeder
     */
    @Transactional
    public RefreshToken createRefreshToken(UUID userId) {
        // Kullanıcının eski refresh token'larını sil (her seferinde yeni token)
        refreshTokenRepository.deleteByUserId(userId);

        String token = jwtService.generateRefreshToken();
        Instant expiryDate = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());

        RefreshToken refreshToken = new RefreshToken(token, userId, expiryDate);
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Token'ı doğrular ve döndürür
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    /**
     * Token string'inden RefreshToken objesini bulur
     */
    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));
    }


    /**
     * Belirli bir refresh token'ı siler
     */
    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(refreshTokenRepository::delete);
    }

    /**
     * Süresi dolmuş token'ları temizler (Her gece saat 3'te çalışır)
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        int deletedCount = refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
        if (deletedCount > 0) {
            LOGGER.info("Cleaned up {} expired refresh tokens", deletedCount);
        }
    }
}

