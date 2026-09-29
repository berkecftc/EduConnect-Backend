package com.educonnect.clubservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ClubPosition {

    PRESIDENT("ROLE_CLUB_OFFICIAL", "Kulüp Başkanı", true, 1),
    VICE_PRESIDENT("ROLE_VICE_PRESIDENT", "Başkan Yardımcısı", true, 1),
    GENERAL_SECRETARY("ROLE_SECRETARY", "Genel Sekreter", true, 1),
    TREASURER("ROLE_TREASURER", "Sayman", true, 1),
    BOARD_MEMBER("ROLE_BOARD_MEMBER", "Yönetim Kurulu Üyesi", true, 7),
    MEMBER("ROLE_MEMBER", "Üye", false, Integer.MAX_VALUE);

    private static volatile boolean legacyApiNames = true;

    private final String legacyName;
    private final String displayName;
    private final boolean management;
    private final int maxActiveHolders;

    ClubPosition(String legacyName, String displayName, boolean management, int maxActiveHolders) {
        this.legacyName = legacyName;
        this.displayName = displayName;
        this.management = management;
        this.maxActiveHolders = maxActiveHolders;
    }

    public boolean isManagement() {
        return management;
    }

    public int maxActiveHolders() {
        return maxActiveHolders;
    }

    public String legacyName() {
        return legacyName;
    }

    public String displayName() {
        return displayName;
    }

    @JsonValue
    public String apiName() {
        return legacyApiNames ? legacyName : name();
    }

    @JsonCreator
    public static ClubPosition fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(position -> position.name().equalsIgnoreCase(normalized)
                        || position.legacyName.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown club position: " + value));
    }

    public static void useLegacyApiNames(boolean enabled) {
        legacyApiNames = enabled;
    }
}
