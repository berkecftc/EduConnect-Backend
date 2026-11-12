package com.educonnect.llmservice.dto.event;

import java.util.UUID;

public record PostModerationEvent(
        UUID postId,
        String title,
        String content,
        UUID eventId,
        String targetType,
        UUID commentId
) {

    public boolean comment() {
        return "COMMENT".equals(targetType) && commentId != null;
    }
}
