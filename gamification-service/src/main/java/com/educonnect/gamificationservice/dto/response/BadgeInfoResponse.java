package com.educonnect.gamificationservice.dto.response;

import java.time.LocalDateTime;

public record BadgeInfoResponse(
        String badgeType,
        String name,
        String description,
        String imageUrl,
        LocalDateTime earnedAt
) {
}