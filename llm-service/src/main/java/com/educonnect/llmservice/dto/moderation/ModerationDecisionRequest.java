package com.educonnect.llmservice.dto.moderation;

public record ModerationDecisionRequest(
        String decision,
        String eventId
) {
}

