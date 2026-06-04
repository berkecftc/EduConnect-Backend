package com.educonnect.gamificationservice.controller;

import com.educonnect.gamificationservice.dto.response.GamificationSummaryResponse;
import com.educonnect.gamificationservice.dto.response.LeaderboardEntryResponse;
import com.educonnect.gamificationservice.model.BadgeType;
import com.educonnect.gamificationservice.service.GamificationService;
import com.educonnect.gamificationservice.util.BadgeSvgProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gamification")
public class GamificationController {

    private final GamificationService gamificationService;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @GetMapping("/users/me/summary")
    public ResponseEntity<GamificationSummaryResponse> getMyGamificationSummary(
            @RequestHeader(value = "X-Authenticated-User-Id", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik dogrulanamadi");
        }
        return ResponseEntity.ok(gamificationService.getUserSummary(UUID.fromString(userId)));
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

    @GetMapping(value = "/badges/{badgeType}/image", produces = "image/svg+xml")
    public ResponseEntity<String> getBadgeImage(@PathVariable String badgeType) {
        BadgeType type;
        try {
            type = BadgeType.valueOf(badgeType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rozet bulunamadi: " + badgeType);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .header("Cache-Control", "public, max-age=86400")
                .body(BadgeSvgProvider.getSvg(type));
    }
}

