package com.educonnect.userservice.client;

import com.educonnect.userservice.client.dto.GamificationSummaryClientResponse;
import com.educonnect.userservice.client.fallback.GamificationClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "gamification-service",
        path = "/api/gamification",
        fallbackFactory = GamificationClientFallbackFactory.class
)
public interface GamificationClient {

    @GetMapping("/internal/users/{userId}/summary")
    GamificationSummaryClientResponse getUserSummary(@PathVariable("userId") UUID userId);
}

