package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.ModerationTarget;

import java.time.Instant;
import java.util.UUID;

public record ModerationQueueItem(
        ModerationTarget targetType,
        UUID id,
        UUID postId,
        String title,
        String content,
        UUID authorId,
        String authorName,
        String status,
        String flag,
        Instant submittedAt
) {}
