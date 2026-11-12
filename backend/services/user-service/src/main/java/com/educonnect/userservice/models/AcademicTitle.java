package com.educonnect.userservice.models;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AcademicTitle {
    PROFESSOR("Prof. Dr.", StaffCategory.FACULTY_MEMBER),
    ASSOCIATE_PROFESSOR("Doç. Dr.", StaffCategory.FACULTY_MEMBER),
    ASSISTANT_PROFESSOR("Dr. Öğr. Üyesi", StaffCategory.FACULTY_MEMBER),
    LECTURER_PHD("Öğr. Gör. Dr.", StaffCategory.LECTURER),
    LECTURER("Öğr. Gör.", StaffCategory.LECTURER),
    RESEARCH_ASSISTANT_PHD("Arş. Gör. Dr.", StaffCategory.RESEARCH_ASSISTANT),
    RESEARCH_ASSISTANT("Arş. Gör.", StaffCategory.RESEARCH_ASSISTANT);

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final String TURKISH = "çğıöşü";
    private static final String ASCII = "cgiosu";

    private final String label;
    private final StaffCategory category;

    AcademicTitle(String label, StaffCategory category) {
        this.label = label;
        this.category = category;
    }

    public String label() {
        return label;
    }

    public StaffCategory category() {
        return category;
    }

    public static Optional<AcademicTitle> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String key = normalize(value);
        return Arrays.stream(values())
                .filter(title -> title.name().equalsIgnoreCase(value.strip()) || normalize(title.label).equals(key))
                .findFirst();
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
