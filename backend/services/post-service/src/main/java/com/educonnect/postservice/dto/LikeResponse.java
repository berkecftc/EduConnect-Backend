package com.educonnect.postservice.dto;

/**
 * Beğeni toggle yanıt DTO'su.
 */
public record LikeResponse(
        boolean liked,
        long likeCount
) {}

