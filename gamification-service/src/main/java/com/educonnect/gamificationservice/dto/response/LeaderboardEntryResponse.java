package com.educonnect.gamificationservice.dto.response;

import java.util.List;

public record LeaderboardEntryResponse(
        int rank,
        String displayName,
        long points,
        List<String> badges,
        boolean me
) {
}
