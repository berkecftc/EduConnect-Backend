package com.educonnect.userservice.service;

import com.educonnect.userservice.client.GamificationClient;
import com.educonnect.userservice.client.PostClient;
import com.educonnect.userservice.client.dto.GamificationSummaryClientResponse;
import com.educonnect.userservice.client.dto.RecentPostClientResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileRemoteService {

    private final GamificationClient gamificationClient;
    private final PostClient postClient;

    public ProfileRemoteService(GamificationClient gamificationClient, PostClient postClient) {
        this.gamificationClient = gamificationClient;
        this.postClient = postClient;
    }

    @CircuitBreaker(name = "gamificationService", fallbackMethod = "gamificationFallback")
    public GamificationSummaryClientResponse getGamificationSummary(UUID userId) {
        return gamificationClient.getUserSummary(userId);
    }

    @CircuitBreaker(name = "postService", fallbackMethod = "recentPostsFallback")
    public List<RecentPostClientResponse> getRecentPosts(UUID userId) {
        return postClient.getRecentPosts(userId);
    }

    private GamificationSummaryClientResponse gamificationFallback(UUID userId, Throwable throwable) {
        GamificationSummaryClientResponse fallback = new GamificationSummaryClientResponse();
        fallback.setTotalPoints(0);
        fallback.setCurrentStreak(0);
        fallback.setHighestStreak(0);
        fallback.setBadges(List.of());
        return fallback;
    }

    private List<RecentPostClientResponse> recentPostsFallback(UUID userId, Throwable throwable) {
        return List.of();
    }
}

