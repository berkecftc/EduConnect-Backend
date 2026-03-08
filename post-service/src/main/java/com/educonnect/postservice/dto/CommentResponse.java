package com.educonnect.postservice.dto;

import com.educonnect.postservice.model.CommentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Yorum yanıt DTO'su.
 * replies alanı sadece üst seviye yorumlar için dolu gelir; yanıtların replies'ı her zaman boş listedir.
 */
public record CommentResponse(
        UUID id,
        UUID postId,
        UUID authorId,
        String authorName,
        UUID parentCommentId,
        String content,
        CommentStatus status,
        List<CommentResponse> replies,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

