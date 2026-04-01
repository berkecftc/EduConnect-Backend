package com.educonnect.gamificationservice.dto.response;

public record LeaderboardEntryResponse(
        int rank,
        String fullName,
        int totalPoints,
        int currentStreak
) {
}


