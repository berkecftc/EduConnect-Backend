package com.educonnect.gamificationservice.dto.response;

import java.time.Instant;

public record BadgeInfoResponse(
        String badgeType,
        String name,
        String description,
        String imageUrl,
        Instant earnedAt
) {
}
