package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.model.PublisherType;

import java.time.LocalDateTime;
import java.util.UUID;

public record PostResponse(
        UUID id,
        String title,
        String content,
        PostCategory category,
        PostStatus status,
        PublisherType publisherType,
        UUID clubId,
        UUID courseId,
        String publisherName,
        boolean official,
        boolean commentsDisabled,
        String reviewNote,
        UUID authorId,
        String authorName,
        String authorDepartment,
        long likeCount,
        long commentCount,
        boolean liked,
        boolean bookmarked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
