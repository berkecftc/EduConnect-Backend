package com.educonnect.postservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentPostDto(
        UUID id,
        String title,
        String content,
        LocalDateTime createdAt
) {
}

