package com.educonnect.gamificationservice.controller;

import com.educonnect.gamificationservice.dto.response.GamificationSummaryResponse;
import com.educonnect.gamificationservice.service.GamificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

