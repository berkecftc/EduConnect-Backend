package com.educonnect.postservice.dto;

import java.time.Instant;
import java.util.UUID;

public record RecentPostDto(
        UUID id,
        String title,
        String content,
        Instant createdAt
) {
}
