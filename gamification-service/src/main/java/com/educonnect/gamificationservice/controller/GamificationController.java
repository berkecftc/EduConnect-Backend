package com.educonnect.gamificationservice.controller;

import com.educonnect.gamificationservice.dto.response.GamificationSummaryResponse;
import com.educonnect.gamificationservice.dto.response.LeaderboardEntryResponse;
import com.educonnect.gamificationservice.service.GamificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gamification")
public class GamificationController {

    private final GamificationService gamificationService;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @GetMapping("/internal/users/{userId}/summary")
    public ResponseEntity<GamificationSummaryResponse> getUserSummary(@PathVariable UUID userId) {
        return ResponseEntity.ok(gamificationService.getUserSummary(userId));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntryResponse>> getLeaderboard(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(gamificationService.getLeaderboard(limit));
    }
}

