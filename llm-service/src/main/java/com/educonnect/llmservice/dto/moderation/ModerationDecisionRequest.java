package com.educonnect.llmservice.dto.moderation;

public record ModerationDecisionRequest(
        String decision,
        String eventId,
        String source
) {

    public static final String WORDLIST = "WORDLIST";
    public static final String LLM = "LLM";
    public static final String UNDECIDED = "INCELEME";
}
