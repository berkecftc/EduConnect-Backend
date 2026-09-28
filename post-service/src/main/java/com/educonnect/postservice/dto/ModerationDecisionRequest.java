package com.educonnect.postservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ModerationDecisionRequest(
        @NotBlank(message = "Moderasyon kararı boş olamaz")
        String decision,
        String eventId
) {
}

