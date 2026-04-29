package com.educonnect.postservice.dto;

import java.util.Locale;
import java.util.Optional;

public enum ModerationDecision {
    ZORBA,
    TEMIZ;

    public static Optional<ModerationDecision> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ModerationDecision.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}

