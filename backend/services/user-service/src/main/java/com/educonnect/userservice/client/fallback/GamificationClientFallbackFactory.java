package com.educonnect.userservice.client.fallback;

import com.educonnect.userservice.client.GamificationClient;
import com.educonnect.userservice.client.dto.GamificationSummaryClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GamificationClientFallbackFactory implements FallbackFactory<GamificationClient> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GamificationClientFallbackFactory.class);

    @Override
    public GamificationClient create(Throwable cause) {
        String reason = cause != null ? cause.getMessage() : "unknown";
        return new GamificationClient() {
            @Override
            public GamificationSummaryClientResponse getUserSummary(UUID userId) {
                LOGGER.warn("gamification-service fallback devrede. userId={}, reason={}", userId, reason);
                GamificationSummaryClientResponse defaultResponse = new GamificationSummaryClientResponse();
                defaultResponse.setTotalPoints(0);
                defaultResponse.setCurrentStreak(0);
                defaultResponse.setHighestStreak(0);
                defaultResponse.setBadges(List.of());
                return defaultResponse;
            }
        };
    }
}


