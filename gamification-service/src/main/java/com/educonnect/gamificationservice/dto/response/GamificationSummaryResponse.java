package com.educonnect.gamificationservice.dto.response;

import java.util.List;

public record GamificationSummaryResponse(
        int totalPoints,
        int currentStreak,
        int highestStreak,
        List<String> badges
) {
}

