package com.educonnect.postservice.dto;

public record ModerationDecisionRequest(
        String decision,
        String eventId
) {
}

