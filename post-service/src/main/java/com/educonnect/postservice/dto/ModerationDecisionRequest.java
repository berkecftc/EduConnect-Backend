package com.educonnect.postservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ModerationDecisionRequest(
        @NotBlank(message = "Moderasyon kararı boş olamaz")
        String decision,
        String eventId,
        String source
) {

    public static final String WORDLIST = "WORDLIST";

    public boolean fromWordList() {
        return WORDLIST.equalsIgnoreCase(source);
    }
}
