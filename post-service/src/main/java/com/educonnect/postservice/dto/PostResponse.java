package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PostResponse(
        UUID id,
        String title,
        String content,
        PostCategory category,
        PostStatus status,
        UUID authorId,
        String authorName,
        String authorDepartment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

