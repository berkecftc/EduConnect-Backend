package com.educonnect.authservices.service;

import com.educonnect.common.web.BadRequestException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AcademicTitles {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final String TURKISH = "çğıöşü";
    private static final String ASCII = "cgiosu";
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("PROFESSOR", "Prof. Dr.");
        LABELS.put("ASSOCIATE_PROFESSOR", "Doç. Dr.");
        LABELS.put("ASSISTANT_PROFESSOR", "Dr. Öğr. Üyesi");
        LABELS.put("LECTURER_PHD", "Öğr. Gör. Dr.");
        LABELS.put("LECTURER", "Öğr. Gör.");
        LABELS.put("RESEARCH_ASSISTANT_PHD", "Arş. Gör. Dr.");
        LABELS.put("RESEARCH_ASSISTANT", "Arş. Gör.");
    }

    private AcademicTitles() {
    }

    public static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("TITLE_REQUIRED", "Unvan zorunludur.");
        }
        String key = normalize(value);
        return LABELS.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(value.strip()) || normalize(entry.getValue()).equals(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("INVALID_TITLE",
                        "Unvan şunlardan biri olmalı: " + String.join(", ", LABELS.values()) + "."));
    }

    private static String normalize(String value) {
        String folded = value.toLowerCase(TR).replaceAll("[\\s.]", "");
        StringBuilder ascii = new StringBuilder(folded.length());
        for (char c : folded.toCharArray()) {
            int index = TURKISH.indexOf(c);
            ascii.append(index < 0 ? c : ASCII.charAt(index));
        }
        return ascii.toString();
    }
}
