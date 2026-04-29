package com.educonnect.llmservice.util;

import com.educonnect.llmservice.dto.moderation.ModerationDecision;

import java.util.Locale;
import java.util.Optional;

public final class ModerationDecisionParser {

    private ModerationDecisionParser() {
    }

    public static Optional<ModerationDecision> parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Optional.empty();
        }

        String normalized = rawResponse.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("ZORBA")) {
            return Optional.of(ModerationDecision.ZORBA);
        }
        if (normalized.contains("TEMIZ")) {
            return Optional.of(ModerationDecision.TEMIZ);
        }
        return Optional.empty();
    }
}

