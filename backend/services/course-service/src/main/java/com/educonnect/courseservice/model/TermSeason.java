package com.educonnect.courseservice.model;

public enum TermSeason {
    FALL("Güz"),
    SPRING("Bahar"),
    SUMMER("Yaz");

    private final String displayName;

    TermSeason(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
